package com.erp.sales.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.framework.security.SecurityUtils;
import com.erp.sales.constant.SalesConstants;
import com.erp.sales.dto.form.SalOrderDetailForm;
import com.erp.sales.dto.form.SalOrderForm;
import com.erp.sales.dto.vo.CustomerBriefVO;
import com.erp.sales.dto.vo.SalOrderDetailBundle;
import com.erp.sales.dto.vo.SalOrderDetailVO;
import com.erp.sales.dto.vo.SalOrderVO;
import com.erp.sales.entity.SalOrder;
import com.erp.sales.entity.SalOrderDetail;
import com.erp.sales.mapper.SalOrderDetailMapper;
import com.erp.sales.mapper.SalOrderMapper;
import com.erp.sales.mapper.SalesBizMapper;
import com.erp.sales.service.SalOrderService;
import com.erp.sales.service.SalesNoGenerator;
import com.erp.workflow.dto.vo.StartResultVO;
import com.erp.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 销售订单 Service 实现。
 * <p>
 * 关键设计：
 * <ul>
 *   <li>金额计算全链 BigDecimal，行金额四舍五入 2 位（HALF_UP）后汇总；</li>
 *   <li>审核 = 信用校验 + 审批流三段式 + 条件状态更新（防并发重复审核）；</li>
 *   <li>跨模块只读（base_customer / fin_receivable）全部走 {@link SalesBizMapper} 约定 SQL。</li>
 * </ul>
 * 审核三段式（审批流接入，与采购订单同一约定）：
 * 首次审核 startFlow（bizType=sal_order，vars.amount=订单总额），SKIPPED/AGREED 免审直过；
 * RUNNING 不执行审核、置 wf_started=1 并返回"已提交审批"；审批通过后再审 isApproved=true
 * 执行原审核；驳回后再审=重新发起（新实例）；已发起过流程的草稿允许关闭作废。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalOrderServiceImpl implements SalOrderService {

    /** 审批流单据类型（dict:wf_biz_type，与 erp-workflow 白名单一致） */
    private static final String WF_BIZ_TYPE = "sal_order";

    /** 审批流条件变量标准名（与节点条件表达式 amount>10000 对齐） */
    private static final String WF_VAR_AMOUNT = "amount";

    /** 发起结果状态：RUNNING=审批中（不执行原审核） */
    private static final String WF_STATUS_RUNNING = "RUNNING";

    private final SalOrderMapper orderMapper;
    private final SalOrderDetailMapper orderDetailMapper;
    private final SalesBizMapper bizMapper;
    private final SalesNoGenerator noGenerator;
    /** 审批引擎（三段式审核，SKIPPED 时免审直过保持一期行为） */
    private final WorkflowService workflowService;

    @Override
    public PageResult<SalOrderVO> page(long pageNum, long pageSize, String keyword, Integer status) {
        Page<SalOrderVO> page = bizMapper.selectOrderPage(new Page<>(pageNum, pageSize), keyword, status);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public SalOrderDetailBundle getDetail(Long id) {
        SalOrderVO order = requireOrderVo(id);
        List<SalOrderDetailVO> details = bizMapper.selectOrderDetails(id);
        return new SalOrderDetailBundle(order, details);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SalOrderForm form) {
        validateMasterData(form);

        SalOrder order = new SalOrder();
        order.setOrderNo(noGenerator.next(SalesConstants.NO_PREFIX_ORDER,
                () -> orderMapper.selectCount(Wrappers.lambdaQuery(SalOrder.class)
                        .likeRight(SalOrder::getOrderNo, todayPrefix()))));
        order.setCustomerId(form.getCustomerId());
        order.setWarehouseId(form.getWarehouseId());
        order.setOrderDate(form.getOrderDate());
        order.setStatus(SalesConstants.ORDER_DRAFT);
        order.setRemark(form.getRemark());
        order.setTotalQty(BigDecimal.ZERO.setScale(4));
        order.setTotalAmount(BigDecimal.ZERO.setScale(2));
        orderMapper.insert(order);
        insertDetails(order.getId(), form.getDetails());
        refreshTotals(order.getId());
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SalOrderForm form) {
        SalOrder order = requireOrder(id);
        if (order.getStatus() != SalesConstants.ORDER_DRAFT) {
            throw BusinessException.ofKey("erp.sal.order-only-draft-modify");
        }
        validateMasterData(form);

        // 草稿修改：明细整体替换（逻辑删旧行 + 插新行），再重算合计
        order.setCustomerId(form.getCustomerId());
        order.setWarehouseId(form.getWarehouseId());
        order.setOrderDate(form.getOrderDate());
        order.setRemark(form.getRemark());
        orderMapper.updateById(order);
        orderDetailMapper.delete(Wrappers.lambdaQuery(SalOrderDetail.class)
                .eq(SalOrderDetail::getOrderId, id));
        insertDetails(id, form.getDetails());
        refreshTotals(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String audit(Long id) {
        SalOrder order = requireOrder(id);
        if (order.getStatus() != SalesConstants.ORDER_DRAFT) {
            throw BusinessException.ofKey("erp.sal.order-only-draft-audit");
        }
        // 审计日志：审核含信用校验与审批流三段式，结果不确定，入口先留痕
        log.info("[销售订单][审核] 开始 订单={} 客户id={} 金额={} 操作人={}",
                order.getOrderNo(), order.getCustomerId(),
                order.getTotalAmount() == null ? "0" : order.getTotalAmount().toPlainString(),
                SecurityUtils.getCurrentUserId());
        checkCredit(order);

        Map<String, Object> vars = Map.of(WF_VAR_AMOUNT,
                order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount());
        boolean started = order.getWfStarted() != null && order.getWfStarted() == 1;
        if (!started) {
            // 首次审核：发起流程（无启用流程 SKIPPED / 条件全不满足 AGREED -> 免审直过）
            StartResultVO r = workflowService.startFlow(WF_BIZ_TYPE, id, order.getOrderNo(), vars);
            if (WF_STATUS_RUNNING.equals(r.getStatus())) {
                markWfStarted(id, true);
                return "已提交审批";
            }
        } else if (workflowService.isApproved(WF_BIZ_TYPE, id)) {
            // 曾发起过且最新实例已 AGREED（审批人已通过）-> 执行原审核
            doAudit(id);
            return "审批已通过，订单审核完成";
        } else {
            // RUNNING（幂等提示）或 REJECTED（驳回后重新提交=新实例）
            StartResultVO r = workflowService.startFlow(WF_BIZ_TYPE, id, order.getOrderNo(), vars);
            if (WF_STATUS_RUNNING.equals(r.getStatus())) {
                return "已提交审批";
            }
        }
        doAudit(id);
        log.info("[销售订单][审核] 完成 订单={} 金额={} 结果=审核通过 操作人={}",
                order.getOrderNo(),
                order.getTotalAmount() == null ? "0" : order.getTotalAmount().toPlainString(),
                SecurityUtils.getCurrentUserId());
        return "审核成功";
    }

    /** 原审核逻辑（信用校验后的条件状态更新 0→10，audit_by=当前登录用户） */
    private void doAudit(Long id) {
        int rows = bizMapper.auditOrder(id, SecurityUtils.getCurrentUserId());
        if (rows == 0) {
            // 并发兜底：其他请求已完成审核/删除，本次拒绝
            throw BusinessException.ofKey("erp.sal.order-status-changed-audit-failed");
        }
    }

    /** 置审批流发起标记（三段式第二/三段的判定依据） */
    private void markWfStarted(Long id, boolean started) {
        SalOrder upd = new SalOrder();
        upd.setId(id);
        upd.setWfStarted(started ? 1 : 0);
        orderMapper.updateById(upd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        SalOrder order = requireOrder(id);
        if (order.getStatus() != SalesConstants.ORDER_AUDITED
                && order.getStatus() != SalesConstants.ORDER_PART_OUT
                // 已发起过审批流的草稿（驳回/审批中）允许关闭作废（三段式约定）
                && !(order.getStatus() == SalesConstants.ORDER_DRAFT
                        && order.getWfStarted() != null && order.getWfStarted() == 1)) {
            throw BusinessException.ofKey("erp.sal.order-close-status-not-allowed");
        }
        int rows = bizMapper.closeOrder(id, SecurityUtils.getCurrentUserId());
        if (rows == 0) {
            throw BusinessException.ofKey("erp.sal.order-status-changed-close-failed");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SalOrder order = requireOrder(id);
        if (order.getStatus() != SalesConstants.ORDER_DRAFT) {
            throw BusinessException.ofKey("erp.sal.order-only-draft-delete");
        }
        orderMapper.deleteById(id);
        orderDetailMapper.delete(Wrappers.lambdaQuery(SalOrderDetail.class)
                .eq(SalOrderDetail::getOrderId, id));
    }

    // ==================== 私有方法 ====================

    /** 校验客户/仓库存在（按约定表结构只读查询，不依赖 basedata 代码）。 */
    private void validateMasterData(SalOrderForm form) {
        CustomerBriefVO customer = bizMapper.selectCustomerBrief(form.getCustomerId());
        if (customer == null) {
            throw BusinessException.ofKey("erp.sal.customer-not-exists", form.getCustomerId());
        }
        if (bizMapper.selectWarehouseName(form.getWarehouseId()) == null) {
            throw BusinessException.ofKey("erp.sal.warehouse-not-exists", form.getWarehouseId());
        }
    }

    /** 信用额度校验：信用额度 − 未收回应收余额 − 本单金额 ≥ 0。credit_limit 为 NULL 视为不控制。 */
    private void checkCredit(SalOrder order) {
        CustomerBriefVO customer = bizMapper.selectCustomerBrief(order.getCustomerId());
        if (customer == null) {
            throw BusinessException.ofKey("erp.sal.customer-not-exists", order.getCustomerId());
        }
        BigDecimal creditLimit = customer.getCreditLimit();
        if (creditLimit == null) {
            return; // 未设置信用额度 = 不控制
        }
        BigDecimal unsettled = bizMapper.sumUnsettledReceivable(order.getCustomerId());
        BigDecimal available = creditLimit.subtract(unsettled).subtract(order.getTotalAmount());
        if (available.compareTo(BigDecimal.ZERO) < 0) {
            // warn：信用超限拒绝是风控关键事件，审计需留痕"谁的单被卡、差多少"
            log.warn("[销售订单][审核] 拒绝 订单={} 客户id={} 原因=信用额度不足 额度={} 未收回应收={} 本单金额={} 差额={}",
                    order.getOrderNo(), order.getCustomerId(), creditLimit.toPlainString(),
                    unsettled.toPlainString(), order.getTotalAmount().toPlainString(),
                    available.toPlainString());
            throw BusinessException.ofKey("erp.sal.credit-limit-insufficient", creditLimit.toPlainString(), unsettled.toPlainString(), order.getTotalAmount().toPlainString(), available.toPlainString());
        }
    }

    /** 插入明细行并计算行金额。 */
    private void insertDetails(Long orderId, List<SalOrderDetailForm> details) {
        for (SalOrderDetailForm d : details) {
            SalOrderDetail entity = new SalOrderDetail();
            entity.setOrderId(orderId);
            entity.setMaterialId(d.getMaterialId());
            entity.setQty(d.getQty());
            entity.setPrice(d.getPrice());
            entity.setAmount(lineAmount(d.getQty(), d.getPrice()));
            entity.setRemark(d.getRemark());
            orderDetailMapper.insert(entity);
        }
    }

    /** 重算表头合计（数量 4 位、金额 2 位）。 */
    private void refreshTotals(Long orderId) {
        List<SalOrderDetail> details = orderDetailMapper.selectList(
                Wrappers.lambdaQuery(SalOrderDetail.class).eq(SalOrderDetail::getOrderId, orderId));
        BigDecimal totalQty = details.stream()
                .map(SalOrderDetail::getQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalAmount = details.stream()
                .map(SalOrderDetail::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        SalOrder update = new SalOrder();
        update.setId(orderId);
        update.setTotalQty(totalQty);
        update.setTotalAmount(totalAmount);
        orderMapper.updateById(update);
    }

    private SalOrder requireOrder(Long id) {
        SalOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw BusinessException.ofKey("erp.sal.order-not-exists", id);
        }
        return order;
    }

    private SalOrderVO requireOrderVo(Long id) {
        SalOrderVO vo = bizMapper.selectOrder(id);
        if (vo == null) {
            throw BusinessException.ofKey("erp.sal.order-not-exists", id);
        }
        return vo;
    }

    /** 行金额 = qty × price，四舍五入 2 位（HALF_UP，全系统统一）。 */
    private BigDecimal lineAmount(BigDecimal qty, BigDecimal price) {
        return qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }

    /** 今日单号前缀（降级计数用），如 SO20260905。 */
    private String todayPrefix() {
        return SalesConstants.NO_PREFIX_ORDER + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }
}
