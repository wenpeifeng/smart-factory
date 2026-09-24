package com.erp.sales.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.framework.security.SecurityUtils;
import com.erp.sales.constant.SalesConstants;
import com.erp.sales.constant.SalesLinkConstants;
import com.erp.sales.dto.form.NoticeSettleForm;
import com.erp.sales.dto.form.SalDeliveryNoticeForm;
import com.erp.sales.dto.form.SalDeliveryNoticeItemForm;
import com.erp.sales.dto.vo.OutstockLineVO;
import com.erp.sales.dto.vo.PendingStockVO;
import com.erp.sales.dto.vo.SalNoticeDetailBundle;
import com.erp.sales.dto.vo.SalNoticeItemVO;
import com.erp.sales.dto.vo.SalNoticeVO;
import com.erp.sales.dto.vo.SalOutstockVO;
import com.erp.sales.entity.SalDeliveryNotice;
import com.erp.sales.entity.SalDeliveryNoticeItem;
import com.erp.sales.entity.SalOrder;
import com.erp.sales.entity.SalOrderDetail;
import com.erp.sales.mapper.SalDeliveryNoticeItemMapper;
import com.erp.sales.mapper.SalDeliveryNoticeMapper;
import com.erp.sales.mapper.SalOrderDetailMapper;
import com.erp.sales.mapper.SalOrderMapper;
import com.erp.sales.mapper.SalesBizMapper;
import com.erp.sales.mapper.SalesLinkMapper;
import com.erp.sales.service.SalDeliveryNoticeService;
import com.erp.sales.service.SalesNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 销售发货通知单 Service 实现。
 * <p>
 * 关键设计：
 * <ul>
 *   <li>双来源：从订单生成时客户/仓库强制取订单值、明细引用订单行并守卫
 *       "累计通知数量 ≤ 订单行剩余数量（qty − out_qty）"；直发场景
 *       （orderId 为空）客户/仓库/物料手工指定并校验主数据存在；</li>
 *   <li>核销守卫：按出库单号核销时逐行条件 UPDATE（out_qty + delta ≤ qty），
 *       超量整单拒绝回滚；全部核销到位自动流转 20已出库；</li>
 *   <li>取消守卫：任一明细已发生出库即拒绝（SQL NOT EXISTS 条件更新，防并发）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalDeliveryNoticeServiceImpl implements SalDeliveryNoticeService {

    private final SalDeliveryNoticeMapper noticeMapper;
    private final SalDeliveryNoticeItemMapper noticeItemMapper;
    private final SalesLinkMapper linkMapper;
    private final SalesBizMapper bizMapper;
    private final SalesNoGenerator noGenerator;
    private final SalOrderMapper orderMapper;
    private final SalOrderDetailMapper orderDetailMapper;

    @Override
    public PageResult<SalNoticeVO> page(long pageNum, long pageSize, String keyword, Integer status) {
        Page<SalNoticeVO> page = linkMapper.selectNoticePage(new Page<>(pageNum, pageSize), keyword, status);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public SalNoticeDetailBundle getDetail(Long id) {
        SalNoticeVO vo = linkMapper.selectNotice(id);
        if (vo == null) {
            throw BusinessException.ofKey("erp.sal.notice-not-exists", id);
        }
        return new SalNoticeDetailBundle(vo, linkMapper.selectNoticeItems(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SalDeliveryNoticeForm form) {
        PreparedNotice prepared = prepare(form, null);

        SalDeliveryNotice n = new SalDeliveryNotice();
        n.setNoticeNo(noGenerator.next(SalesLinkConstants.NO_PREFIX_NOTICE,
                () -> noticeMapper.selectCount(Wrappers.lambdaQuery(SalDeliveryNotice.class)
                        .likeRight(SalDeliveryNotice::getNoticeNo, todayPrefix()))));
        n.setOrderId(form.getOrderId());
        n.setOrderNo(prepared.orderNo);
        n.setCustomerId(prepared.customerId);
        n.setWarehouseId(prepared.warehouseId);
        n.setPlanDate(form.getPlanDate());
        n.setStatus(SalesLinkConstants.NOTICE_DRAFT);
        n.setRemark(form.getRemark());
        noticeMapper.insert(n);
        insertItems(n.getId(), form.getDetails(), prepared);
        return n.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SalDeliveryNoticeForm form) {
        SalDeliveryNotice n = requireNotice(id);
        if (n.getStatus() != SalesLinkConstants.NOTICE_DRAFT) {
            throw BusinessException.ofKey("erp.sal.notice-only-draft-modify");
        }
        // 编辑场景：计算累计通知数量时排除自身，避免余量被自己占用
        PreparedNotice prepared = prepare(form, id);

        n.setOrderId(form.getOrderId());
        n.setOrderNo(prepared.orderNo);
        n.setCustomerId(prepared.customerId);
        n.setWarehouseId(prepared.warehouseId);
        n.setPlanDate(form.getPlanDate());
        n.setRemark(form.getRemark());
        noticeMapper.updateById(n);
        noticeItemMapper.delete(Wrappers.lambdaQuery(SalDeliveryNoticeItem.class)
                .eq(SalDeliveryNoticeItem::getNoticeId, id));
        insertItems(id, form.getDetails(), prepared);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SalDeliveryNotice n = requireNotice(id);
        if (n.getStatus() != SalesLinkConstants.NOTICE_DRAFT) {
            throw BusinessException.ofKey("erp.sal.notice-only-draft-delete");
        }
        noticeMapper.deleteById(id);
        noticeItemMapper.delete(Wrappers.lambdaQuery(SalDeliveryNoticeItem.class)
                .eq(SalDeliveryNoticeItem::getNoticeId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void notifyShip(Long id) {
        requireNotice(id);
        int rows = linkMapper.notifyNotice(id, SecurityUtils.getCurrentUserId());
        if (rows == 0) {
            SalDeliveryNotice n = requireNotice(id);
            throw BusinessException.ofKey("erp.sal.notice-status-no-notify", statusText(n.getStatus()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        requireNotice(id);
        int rows = linkMapper.cancelNotice(id, SecurityUtils.getCurrentUserId());
        if (rows == 0) {
            // 并发兜底：区分"状态不允许"与"已发生出库"
            SalDeliveryNotice n = requireNotice(id);
            if (n.getStatus() == SalesLinkConstants.NOTICE_DRAFT
                    || n.getStatus() == SalesLinkConstants.NOTICE_NOTIFIED) {
                throw BusinessException.ofKey("erp.sal.notice-writeoff-exists-no-cancel");
            }
            throw BusinessException.ofKey("erp.sal.notice-status-no-cancel", statusText(n.getStatus()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settle(Long id, NoticeSettleForm form) {
        SalDeliveryNotice n = requireNotice(id);
        if (n.getStatus() != SalesLinkConstants.NOTICE_NOTIFIED) {
            throw BusinessException.ofKey("erp.sal.notice-only-notified-writeoff", statusText(n.getStatus()));
        }

        // 读取出库单（跨模块只读：sal_outstock，不修改既有出库代码）
        SalOutstockVO outstock = linkMapper.selectOutstockBriefByNo(form.getOutstockNo());
        if (outstock == null) {
            throw BusinessException.ofKey("erp.sal.outstock-not-exists", form.getOutstockNo());
        }
        if (outstock.getStatus() == null || outstock.getStatus() != SalesConstants.BIZ_AUDITED) {
            throw BusinessException.ofKey("erp.sal.outstock-not-audited-no-writeoff", form.getOutstockNo());
        }
        List<OutstockLineVO> lines = linkMapper.selectOutstockLines(form.getOutstockNo());
        List<SalNoticeItemVO> items = linkMapper.selectNoticeItems(id);
        if (items.isEmpty()) {
            throw BusinessException.ofKey("erp.sal.notice-no-lines-no-writeoff", n.getNoticeNo());
        }

        // 匹配：有订单行引用的通知行按 orderDetailId+material 精确匹配；直发行按物料匹配
        List<SalNoticeItemVO> matched = new ArrayList<>();
        List<BigDecimal> deltas = new ArrayList<>();
        for (SalNoticeItemVO item : items) {
            BigDecimal delta = BigDecimal.ZERO;
            for (OutstockLineVO line : lines) {
                boolean sameMaterial = line.getMaterialId() != null
                        && line.getMaterialId().equals(item.getMaterialId());
                if (!sameMaterial) {
                    continue;
                }
                if (item.getOrderDetailId() != null) {
                    if (item.getOrderDetailId().equals(line.getOrderDetailId())) {
                        delta = delta.add(line.getQty());
                    }
                } else {
                    delta = delta.add(line.getQty());
                }
            }
            if (delta.compareTo(BigDecimal.ZERO) > 0) {
                matched.add(item);
                deltas.add(delta);
            }
        }
        if (matched.isEmpty()) {
            throw BusinessException.ofKey("erp.sal.material-mismatch-no-writeoff", form.getOutstockNo(), n.getNoticeNo());
        }

        // 守卫：核销数量不得超过通知行剩余数量（超量整单拒绝回滚）
        for (int i = 0; i < matched.size(); i++) {
            SalNoticeItemVO item = matched.get(i);
            BigDecimal remaining = item.getQty().subtract(item.getOutQty());
            if (deltas.get(i).compareTo(remaining) > 0) {
                throw BusinessException.ofKey("erp.sal.writeoff-over-remaining", item.getMaterialName() == null ? item.getMaterialId() : item.getMaterialName(), deltas.get(i).toPlainString(), remaining.toPlainString());
            }
        }

        // 条件累加（out_qty + delta ≤ qty 双保险，防并发双击/重复核销）
        for (int i = 0; i < matched.size(); i++) {
            int rows = linkMapper.addNoticeOutQty(matched.get(i).getId(), deltas.get(i));
            if (rows == 0) {
                throw BusinessException.ofKey("erp.sal.notice-line-writeoff-conflict");
            }
        }
        linkMapper.markNoticeSettled(id, SecurityUtils.getCurrentUserId());
        int finished = linkMapper.refreshNoticeOut(id);
        log.info("[发货通知][核销] 通知单={} 出库单={} 核销行数={} 完成={} 操作人={}",
                n.getNoticeNo(), form.getOutstockNo(), matched.size(),
                finished > 0 ? "是(已出库)" : "否(部分出库)", SecurityUtils.getCurrentUserId());
    }

    @Override
    public List<PendingStockVO> pendingStocks(Long warehouseId, String keyword) {
        return linkMapper.selectPendingStocks(warehouseId, keyword);
    }

    // ==================== 私有方法 ====================

    /** 表单校验结果（客户/仓库/订单行上下文）。 */
    private static class PreparedNotice {
        Long customerId;
        Long warehouseId;
        String orderNo;
    }

    /**
     * 双来源校验：
     * 带订单 → 订单存在且[已审核/部分出库]，客户/仓库强制取订单值，明细必须引用订单行，
     * 守卫"累计通知 ≤ 订单行剩余"；直发 → 客户/仓库必填且存在，明细必须填物料。
     */
    private PreparedNotice prepare(SalDeliveryNoticeForm form, Long excludeNoticeId) {
        PreparedNotice prepared = new PreparedNotice();
        if (form.getOrderId() != null) {
            SalOrder order = orderMapper.selectById(form.getOrderId());
            if (order == null) {
                throw BusinessException.ofKey("erp.sal.order-not-exists", form.getOrderId());
            }
            if (order.getStatus() != SalesConstants.ORDER_AUDITED
                    && order.getStatus() != SalesConstants.ORDER_PART_OUT) {
                throw BusinessException.ofKey("erp.sal.order-status-no-notice", orderStatusText(order.getStatus()));
            }
            // 来源单据是唯一事实：客户/仓库强制取订单值，忽略表单传入
            prepared.customerId = order.getCustomerId();
            prepared.warehouseId = order.getWarehouseId();
            prepared.orderNo = order.getOrderNo();
            for (SalDeliveryNoticeItemForm d : form.getDetails()) {
                if (d.getOrderDetailId() == null) {
                    throw BusinessException.ofKey("erp.sal.notice-line-order-ref-required");
                }
                SalOrderDetail od = orderDetailMapper.selectById(d.getOrderDetailId());
                if (od == null || !od.getOrderId().equals(form.getOrderId())) {
                    throw BusinessException.ofKey("erp.sal.order-line-not-exists", d.getOrderDetailId(), order.getOrderNo());
                }
                BigDecimal remaining = od.getQty().subtract(od.getOutQty());
                BigDecimal notified = linkMapper.sumActiveNoticeQty(d.getOrderDetailId(), excludeNoticeId);
                if (notified.add(d.getQty()).compareTo(remaining) > 0) {
                    throw BusinessException.ofKey("erp.sal.notice-over-remaining", od.getMaterialId(), notified.toPlainString(), d.getQty().toPlainString(), remaining.toPlainString());
                }
            }
        } else {
            if (form.getCustomerId() == null) {
                throw BusinessException.ofKey("erp.sal.direct-customer-required");
            }
            if (form.getWarehouseId() == null) {
                throw BusinessException.ofKey("erp.sal.direct-warehouse-required");
            }
            if (bizMapper.selectCustomerBrief(form.getCustomerId()) == null) {
                throw BusinessException.ofKey("erp.sal.customer-not-exists", form.getCustomerId());
            }
            if (bizMapper.selectWarehouseName(form.getWarehouseId()) == null) {
                throw BusinessException.ofKey("erp.sal.warehouse-not-exists", form.getWarehouseId());
            }
            prepared.customerId = form.getCustomerId();
            prepared.warehouseId = form.getWarehouseId();
            for (SalDeliveryNoticeItemForm d : form.getDetails()) {
                if (d.getOrderDetailId() != null) {
                    throw BusinessException.ofKey("erp.sal.direct-line-no-order-ref");
                }
                if (d.getMaterialId() == null) {
                    throw BusinessException.ofKey("erp.sal.direct-line-material-required");
                }
                if (linkMapper.selectMaterialName(d.getMaterialId()) == null) {
                    throw BusinessException.ofKey("erp.sal.material-not-exists", d.getMaterialId());
                }
            }
        }
        return prepared;
    }

    /** 插入明细行（从订单生成时物料取订单行值，服务端赋值防串改）。 */
    private void insertItems(Long noticeId, List<SalDeliveryNoticeItemForm> details, PreparedNotice prepared) {
        for (SalDeliveryNoticeItemForm d : details) {
            SalDeliveryNoticeItem entity = new SalDeliveryNoticeItem();
            entity.setNoticeId(noticeId);
            entity.setQty(d.getQty());
            entity.setOutQty(BigDecimal.ZERO.setScale(4));
            entity.setOrderDetailId(d.getOrderDetailId());
            entity.setRemark(d.getRemark());
            if (d.getOrderDetailId() != null) {
                SalOrderDetail od = orderDetailMapper.selectById(d.getOrderDetailId());
                entity.setMaterialId(od.getMaterialId());
            } else {
                entity.setMaterialId(d.getMaterialId());
            }
            noticeItemMapper.insert(entity);
        }
    }

    private SalDeliveryNotice requireNotice(Long id) {
        SalDeliveryNotice n = noticeMapper.selectById(id);
        if (n == null) {
            throw BusinessException.ofKey("erp.sal.notice-not-exists", id);
        }
        return n;
    }

    private String statusText(int status) {
        return switch (status) {
            case 0 -> "草稿";
            case 10 -> "已通知";
            case 20 -> "已出库";
            case 30 -> "已取消";
            default -> String.valueOf(status);
        };
    }

    private String orderStatusText(int status) {
        return switch (status) {
            case 0 -> "草稿";
            case 10 -> "已审核";
            case 20 -> "部分出库";
            case 30 -> "已完成";
            case 90 -> "已关闭";
            default -> String.valueOf(status);
        };
    }

    /** 今日单号前缀（Redis 降级计数用），如 FH20260905。 */
    private String todayPrefix() {
        return SalesLinkConstants.NO_PREFIX_NOTICE
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
