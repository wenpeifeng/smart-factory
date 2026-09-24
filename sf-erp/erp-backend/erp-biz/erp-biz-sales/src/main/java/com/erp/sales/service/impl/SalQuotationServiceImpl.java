package com.erp.sales.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.framework.security.SecurityUtils;
import com.erp.sales.constant.SalesLinkConstants;
import com.erp.sales.dto.form.SalOrderDetailForm;
import com.erp.sales.dto.form.SalOrderForm;
import com.erp.sales.dto.form.SalQuotationForm;
import com.erp.sales.dto.form.SalQuotationItemForm;
import com.erp.sales.dto.form.SalQuotationToOrderForm;
import com.erp.sales.dto.vo.MaterialCostVO;
import com.erp.sales.dto.vo.QuotationMarginBundle;
import com.erp.sales.dto.vo.QuotationMarginRowVO;
import com.erp.sales.dto.vo.SalQuotationDetailBundle;
import com.erp.sales.dto.vo.SalQuotationItemVO;
import com.erp.sales.dto.vo.SalQuotationVO;
import com.erp.sales.entity.SalOrder;
import com.erp.sales.entity.SalQuotation;
import com.erp.sales.entity.SalQuotationItem;
import com.erp.sales.mapper.SalOrderMapper;
import com.erp.sales.mapper.SalQuotationItemMapper;
import com.erp.sales.mapper.SalQuotationMapper;
import com.erp.sales.mapper.SalesBizMapper;
import com.erp.sales.mapper.SalesLinkMapper;
import com.erp.sales.service.SalOrderService;
import com.erp.sales.service.SalQuotationService;
import com.erp.sales.service.SalesNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 销售报价单 Service 实现。
 * <p>
 * 关键设计：
 * <ul>
 *   <li>成本参考快照：明细录入时从 base_material.ref_cost 取值落库（cost_price），
 *       不接受前端回传，防篡改 + 防物料调价导致历史毛利失真；</li>
 *   <li>行金额 = 数量 × 报价单价 × (1 − 整单折扣率/100)，四舍五入 2 位（HALF_UP）；</li>
 *   <li>"已过期"不落库：查询时以 status=10 且 valid_until 早于当天动态判定；</li>
 *   <li>下推订单复用既有 {@link SalOrderService#create}（同一事务），成功后条件
 *       UPDATE 回写 20已转订单（WHERE 携带"未过期"防与过期判定并发竞态）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalQuotationServiceImpl implements SalQuotationService {

    /** 折扣率上限（%） */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final SalQuotationMapper quotationMapper;
    private final SalQuotationItemMapper quotationItemMapper;
    private final SalesLinkMapper linkMapper;
    private final SalesBizMapper bizMapper;
    private final SalesNoGenerator noGenerator;
    /** 下推订单复用既有订单服务（并行任务占用中，注入调用不改其代码） */
    private final SalOrderService orderService;
    private final SalOrderMapper orderMapper;

    @Override
    public PageResult<SalQuotationVO> page(long pageNum, long pageSize, String keyword,
                                           Long customerId, Integer status,
                                           LocalDate validFrom, LocalDate validTo) {
        Page<SalQuotationVO> page = linkMapper.selectQuotationPage(
                new Page<>(pageNum, pageSize), keyword, customerId, status, validFrom, validTo);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public SalQuotationDetailBundle getDetail(Long id) {
        SalQuotationVO vo = linkMapper.selectQuotation(id);
        if (vo == null) {
            throw BusinessException.ofKey("erp.sal.quote-not-exists", id);
        }
        return new SalQuotationDetailBundle(vo, linkMapper.selectQuotationItems(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SalQuotationForm form) {
        validateForm(form);

        SalQuotation q = new SalQuotation();
        q.setQuotationNo(noGenerator.next(SalesLinkConstants.NO_PREFIX_QUOTATION,
                () -> quotationMapper.selectCount(Wrappers.lambdaQuery(SalQuotation.class)
                        .likeRight(SalQuotation::getQuotationNo, todayPrefix()))));
        q.setCustomerId(form.getCustomerId());
        q.setQuoteDate(form.getQuoteDate());
        q.setValidUntil(form.getValidUntil());
        q.setDiscountRate(form.getDiscountRate());
        q.setStatus(SalesLinkConstants.QUOT_DRAFT);
        q.setRemark(form.getRemark());
        quotationMapper.insert(q);
        insertItems(q.getId(), form.getItems(), form.getDiscountRate());
        return q.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SalQuotationForm form) {
        SalQuotation q = requireQuotation(id);
        if (q.getStatus() != SalesLinkConstants.QUOT_DRAFT) {
            throw BusinessException.ofKey("erp.sal.quote-only-draft-modify");
        }
        validateForm(form);

        // 草稿修改：明细整体替换（逻辑删旧行 + 插新行），成本快照按最新 ref_cost 重取
        q.setCustomerId(form.getCustomerId());
        q.setQuoteDate(form.getQuoteDate());
        q.setValidUntil(form.getValidUntil());
        q.setDiscountRate(form.getDiscountRate());
        q.setRemark(form.getRemark());
        quotationMapper.updateById(q);
        quotationItemMapper.delete(Wrappers.lambdaQuery(SalQuotationItem.class)
                .eq(SalQuotationItem::getQuotationId, id));
        insertItems(id, form.getItems(), form.getDiscountRate());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SalQuotation q = requireQuotation(id);
        if (q.getStatus() != SalesLinkConstants.QUOT_DRAFT) {
            throw BusinessException.ofKey("erp.sal.quote-only-draft-delete");
        }
        quotationMapper.deleteById(id);
        quotationItemMapper.delete(Wrappers.lambdaQuery(SalQuotationItem.class)
                .eq(SalQuotationItem::getQuotationId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        requireQuotation(id);
        int rows = linkMapper.submitQuotation(id, SecurityUtils.getCurrentUserId());
        if (rows == 0) {
            // 并发兜底：按当前状态给出精确提示
            SalQuotation q = requireQuotation(id);
            throw BusinessException.ofKey("erp.sal.quote-status-no-submit", statusText(q.getStatus()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidQuotation(Long id) {
        requireQuotation(id);
        int rows = linkMapper.voidQuotation(id, SecurityUtils.getCurrentUserId());
        if (rows == 0) {
            SalQuotation q = requireQuotation(id);
            throw BusinessException.ofKey("erp.sal.quote-status-no-void", statusText(q.getStatus()));
        }
    }

    @Override
    public QuotationMarginBundle margin(Long id) {
        SalQuotationVO vo = linkMapper.selectQuotation(id);
        if (vo == null) {
            throw BusinessException.ofKey("erp.sal.quote-not-exists", id);
        }
        List<QuotationMarginRowVO> rows = linkMapper.selectQuotationItems(id).stream()
                .map(this::toMarginRow)
                .toList();
        return new QuotationMarginBundle(vo.getQuotationNo(), vo.getStatus(),
                SalesLinkConstants.MARGIN_WARN_THRESHOLD, rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long toOrder(Long id, SalQuotationToOrderForm form) {
        SalQuotation q = requireQuotation(id);
        if (q.getStatus() != SalesLinkConstants.QUOT_QUOTED) {
            throw BusinessException.ofKey("erp.sal.quote-only-quoted-push", statusText(q.getStatus()));
        }
        if (isExpired(q)) {
            throw BusinessException.ofKey("erp.sal.quote-expired-no-push", q.getValidUntil());
        }
        List<SalQuotationItem> items = quotationItemMapper.selectList(
                Wrappers.lambdaQuery(SalQuotationItem.class).eq(SalQuotationItem::getQuotationId, id));
        if (items.isEmpty()) {
            throw BusinessException.ofKey("erp.sal.quote-no-lines-no-push", q.getQuotationNo());
        }

        // 仓库：显式指定优先，否则取第一个在用仓库兜底
        Long warehouseId = form.getWarehouseId();
        if (warehouseId == null) {
            warehouseId = linkMapper.selectFirstWarehouseId();
        }
        if (warehouseId == null || bizMapper.selectWarehouseName(warehouseId) == null) {
            throw BusinessException.ofKey("erp.sal.ship-warehouse-required");
        }

        // 组装订单草稿：单价 = 报价 × (1 − 整单折扣率/100)，备注记来源
        BigDecimal factor = discountFactor(q.getDiscountRate());
        SalOrderForm orderForm = new SalOrderForm();
        orderForm.setCustomerId(q.getCustomerId());
        orderForm.setWarehouseId(warehouseId);
        orderForm.setOrderDate(form.getOrderDate() != null ? form.getOrderDate() : LocalDate.now());
        orderForm.setRemark("报价单生成：" + q.getQuotationNo());
        orderForm.setDetails(items.stream().map(it -> {
            SalOrderDetailForm d = new SalOrderDetailForm();
            d.setMaterialId(it.getMaterialId());
            d.setQty(it.getQty());
            d.setPrice(it.getPrice().multiply(factor).setScale(2, RoundingMode.HALF_UP));
            d.setRemark(it.getRemark());
            return d;
        }).toList());

        // 复用既有订单服务创建（同事务：创建失败整体回滚，报价单状态不变）
        Long orderId = orderService.create(orderForm);
        SalOrder order = orderMapper.selectById(orderId);

        // 回写：条件携带"未过期"，防与过期判定并发竞态
        int rows = linkMapper.markQuotationOrdered(id, orderId, order == null ? null : order.getOrderNo());
        if (rows == 0) {
            throw BusinessException.ofKey("erp.sal.quote-status-changed-push-failed");
        }
        log.info("[报价单][下推] 报价单={} → 订单={} 操作人={}",
                q.getQuotationNo(), order == null ? orderId : order.getOrderNo(),
                SecurityUtils.getCurrentUserId());
        return orderId;
    }

    // ==================== 私有方法 ====================

    /** 表头基础校验：客户存在、有效期不早于报价日、折扣率 0-100。 */
    private void validateForm(SalQuotationForm form) {
        if (bizMapper.selectCustomerBrief(form.getCustomerId()) == null) {
            throw BusinessException.ofKey("erp.sal.customer-not-exists", form.getCustomerId());
        }
        if (form.getValidUntil().isBefore(form.getQuoteDate())) {
            throw BusinessException.ofKey("erp.sal.valid-until-order", form.getValidUntil(), form.getQuoteDate());
        }
        if (form.getDiscountRate() != null
                && (form.getDiscountRate().compareTo(BigDecimal.ZERO) < 0
                    || form.getDiscountRate().compareTo(HUNDRED) > 0)) {
            throw BusinessException.ofKey("erp.sal.discount-rate-range", form.getDiscountRate().toPlainString());
        }
    }

    /** 插入明细行：成本快照批量取 base_material.ref_cost，行金额含整单折扣。 */
    private void insertItems(Long quotationId, List<SalQuotationItemForm> items, BigDecimal discountRate) {
        List<Long> materialIds = items.stream().map(SalQuotationItemForm::getMaterialId).distinct().toList();
        Map<Long, BigDecimal> costMap = linkMapper.selectMaterialCosts(materialIds).stream()
                .collect(Collectors.toMap(MaterialCostVO::getId, MaterialCostVO::getRefCost, (a, b) -> a));
        for (Long mid : materialIds) {
            if (!costMap.containsKey(mid)) {
                throw BusinessException.ofKey("erp.sal.material-not-exists", mid);
            }
        }
        BigDecimal factor = discountFactor(discountRate);
        for (SalQuotationItemForm f : items) {
            SalQuotationItem entity = new SalQuotationItem();
            entity.setQuotationId(quotationId);
            entity.setMaterialId(f.getMaterialId());
            entity.setQty(f.getQty());
            entity.setPrice(f.getPrice());
            entity.setCostPrice(costMap.get(f.getMaterialId()));
            entity.setAmount(f.getQty().multiply(f.getPrice()).multiply(factor)
                    .setScale(2, RoundingMode.HALF_UP));
            entity.setRemark(f.getRemark());
            quotationItemMapper.insert(entity);
        }
    }

    /** 明细行 → 毛利率视图行：毛利率 = (报价 − 成本)/报价，低于警戒线标记。 */
    private QuotationMarginRowVO toMarginRow(SalQuotationItemVO item) {
        QuotationMarginRowVO row = new QuotationMarginRowVO();
        row.setItemId(item.getId());
        row.setMaterialId(item.getMaterialId());
        row.setMaterialCode(item.getMaterialCode());
        row.setMaterialName(item.getMaterialName());
        row.setMaterialSpec(item.getMaterialSpec());
        row.setQty(item.getQty());
        row.setPrice(item.getPrice());
        row.setCostPrice(item.getCostPrice());
        row.setRemark(item.getRemark());

        BigDecimal price = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
        BigDecimal cost = item.getCostPrice() == null ? BigDecimal.ZERO : item.getCostPrice();
        BigDecimal qty = item.getQty() == null ? BigDecimal.ZERO : item.getQty();
        row.setMarginAmount(price.subtract(cost).multiply(qty).setScale(2, RoundingMode.HALF_UP));
        if (price.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal rate = price.subtract(cost).divide(price, 4, RoundingMode.HALF_UP)
                    .multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
            row.setMarginRate(rate);
            row.setWarn(rate.compareTo(SalesLinkConstants.MARGIN_WARN_THRESHOLD) < 0);
        } else {
            // 报价为 0 无法计算比率（毛利额已按负值体现），不参与警戒判定
            row.setMarginRate(null);
            row.setWarn(false);
        }
        return row;
    }

    /** 折扣因子：discountRate 为空 = 不打折；否则 (100 − rate)/100。 */
    private BigDecimal discountFactor(BigDecimal discountRate) {
        if (discountRate == null) {
            return BigDecimal.ONE;
        }
        return HUNDRED.subtract(discountRate).divide(HUNDRED, 6, RoundingMode.HALF_UP);
    }

    /** 已过期判定：已报价 且 有效期至早于当天（有效期当天含在内）。 */
    private boolean isExpired(SalQuotation q) {
        return q.getValidUntil() != null && q.getValidUntil().isBefore(LocalDate.now());
    }

    private SalQuotation requireQuotation(Long id) {
        SalQuotation q = quotationMapper.selectById(id);
        if (q == null) {
            throw BusinessException.ofKey("erp.sal.quote-not-exists", id);
        }
        return q;
    }

    /** 落库状态转文案（报错信息用，不含动态 30）。 */
    private String statusText(int status) {
        return switch (status) {
            case 0 -> "草稿";
            case 10 -> "已报价";
            case 20 -> "已转订单";
            case 40 -> "已作废";
            default -> String.valueOf(status);
        };
    }

    /** 今日单号前缀（Redis 降级计数用），如 BJ20260905。 */
    private String todayPrefix() {
        return SalesLinkConstants.NO_PREFIX_QUOTATION
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
