package com.erp.it;

import com.erp.common.exception.BusinessException;
import com.erp.cost.service.CostService;
import com.erp.finance.dto.form.ReceivableForm;
import com.erp.finance.service.FinReceivableService;
import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.service.StockService;
import com.erp.sales.dto.form.SalOrderDetailForm;
import com.erp.sales.dto.form.SalOrderForm;
import com.erp.sales.dto.form.SalOutstockDetailForm;
import com.erp.sales.dto.form.SalOutstockForm;
import com.erp.sales.dto.form.SalReturnDetailForm;
import com.erp.sales.dto.form.SalReturnForm;
import com.erp.sales.dto.vo.SalOrderDetailBundle;
import com.erp.sales.dto.vo.SalOrderDetailVO;
import com.erp.sales.dto.vo.SalOutstockDetailBundle;
import com.erp.sales.dto.vo.SalOutstockDetailVO;
import com.erp.sales.service.SalOrderService;
import com.erp.sales.service.SalOutstockService;
import com.erp.sales.service.SalReturnService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 链路四：销售信用管控（订单审核信用校验 × 应收/退货红字冲减）。
 * <p>
 * 口径：{@code credit_limit − SUM(未收回应收余额) − 本单金额 ≥ 0} 才允许审核；
 * 未收回应收 = fin_receivable 的 SUM(amount − settled_amount)，销售退货生成
 * <b>红字应收（负金额）</b>，天然冲减客户占用额度——退货后额度释放。
 * <p>
 * 场景（客户额度 1000，期初未收回应收 800）：
 * 下单 150 通过 → 下单 250 拒绝 → 出库 150 占满额度后再下单被拒 →
 * 全额退货红冲 150 → 同一张被拒订单复审通过。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("链路4-信用额度管控与红字冲减")
class CreditControlIT extends ItTestSupport {

    @Autowired
    private SalOrderService orderService;
    @Autowired
    private SalOutstockService outstockService;
    @Autowired
    private SalReturnService returnService;
    @Autowired
    private FinReceivableService receivableService;
    @Autowired
    private StockService stockService;
    @Autowired
    private CostService costService;

    private static final BigDecimal CREDIT_LIMIT = new BigDecimal("1000");

    private Long warehouseId;
    private Long materialId;
    private Long customerId;

    /** 订单 A：金额 150（10 × 15），出库/退货链路载体 */
    private Long orderAId;
    private Long orderADetailId;
    /** 订单 C：金额 100，先被拒、红冲释放额度后复审通过 */
    private Long orderCId;

    private final LocalDate today = LocalDate.now();

    @BeforeAll
    void setUpScenario() {
        // 审核链路需要登录上下文（audit_by / 审批流取当前用户）
        loginAs(1L);

        warehouseId = newWarehouse("IT信用测试仓-" + uniqueSuffix());
        materialId = newMaterial("IT信用物料-" + uniqueSuffix(), false);
        customerId = newCustomer("IT信用客户-" + uniqueSuffix(), CREDIT_LIMIT);

        // 备货 + 计价（出库审核依赖库存与成本台账，"账实不符禁止出库"口径）
        stockService.changeStock(warehouseId, materialId, new BigDecimal("20"),
                InvBizType.OTH_IN, null, "IT-CRD-STK-" + uniqueSuffix());
        costService.onStockIn(materialId, warehouseId, "PUR_IN",
                "IT-CRD-COST-" + uniqueSuffix(), null, new BigDecimal("20"),
                new BigDecimal("200"), today);

        // 期初未收回应收 800（财务手工补录，走业务 Service）
        receivableService.create(new ReceivableForm(customerId,
                "IT-CRD-AR-" + uniqueSuffix(), new BigDecimal("800"),
                today, today.plusDays(30), "IT-信用场景期初应收"));

        assertThat(unsettledReceivable()).isEqualByComparingTo("800");
    }

    @Test
    @Order(1)
    @DisplayName("额度1000/已占用800：下单150通过审核")
    void order150_withinCredit_passesAudit() {
        orderAId = createOrder(new BigDecimal("10"), new BigDecimal("15")); // 150
        SalOrderDetailBundle detail = orderService.getDetail(orderAId);
        orderADetailId = detail.getDetails().get(0).getId();

        String result = orderService.audit(orderAId);
        assertThat(result).as("无启用审批流时应免审直过").contains("审核");

        assertThat(orderStatus(orderAId)).as("订单A已审核").isEqualTo(10);
    }

    @Test
    @Order(2)
    @DisplayName("额度1000/已占用800：下单250被信用校验拒绝，订单停留草稿")
    void order250_exceedingCredit_rejected() {
        Long orderId = createOrder(new BigDecimal("10"), new BigDecimal("25")); // 250
        Throwable tex = catchThrowable(() -> orderService.audit(orderId));
        assertBizMsg(tex, "信用额度不足");
        assertThat(orderStatus(orderId)).as("被拒订单回滚为草稿，可修改重提").isEqualTo(0);
    }

    @Test
    @Order(3)
    @DisplayName("出库占用额度→下单被拒→退货红冲释放→同单复审通过")
    void returnRedLetter_releasesCreditForRetry() {
        // 1. 订单A出库 150：生成蓝字应收，未收回应收 800→950（额度占满，剩 50）
        Long outstockId = outstockService.create(outstockForm(orderAId, orderADetailId,
                new BigDecimal("10")));
        outstockService.audit(outstockId);
        assertThat(unsettledReceivable()).as("出库后未收回应收=950").isEqualByComparingTo("950");

        // 2. 订单C 金额 100：950+100 > 1000 被拒
        orderCId = createOrder(new BigDecimal("10"), new BigDecimal("10")); // 100
        Throwable tex2 = catchThrowable(() -> orderService.audit(orderCId));
        assertBizMsg(tex2, "信用额度不足");
        assertThat(orderStatus(orderCId)).isEqualTo(0);

        // 3. 全额退货 150：生成红字应收（负金额），未收回应收 950→800（额度释放）
        SalOutstockDetailBundle outDetail = outstockService.getDetail(outstockId);
        SalOutstockDetailVO outLine = outDetail.getDetails().get(0);
        Long returnId = returnService.create(returnForm(outstockId, outLine.getId(),
                new BigDecimal("10")));
        returnService.audit(returnId);

        BigDecimal redAmount = jdbc.queryForObject(
                "SELECT amount FROM fin_receivable WHERE biz_no=(SELECT return_no FROM sal_return WHERE id=?)",
                BigDecimal.class, returnId);
        assertThat(redAmount).as("退货生成红字应收（负金额）").isEqualByComparingTo("-150");
        assertThat(unsettledReceivable()).as("红冲后未收回应收回落至800").isEqualByComparingTo("800");

        // 4. 同一张被拒订单复审：1000-800-100=100 ≥ 0 → 通过
        orderService.audit(orderCId);
        assertThat(orderStatus(orderCId)).as("额度释放后订单C复审通过").isEqualTo(10);

        // 库存守恒：出库 10 又退货回补 10，回到 20
        BigDecimal stockQty = jdbc.queryForObject(
                "SELECT COALESCE(SUM(qty),0) FROM inv_stock WHERE warehouse_id=? AND material_id=? "
                        + "AND deleted=0", BigDecimal.class, warehouseId, materialId);
        assertThat(stockQty).as("退货回补后库存复原为20").isEqualByComparingTo("20");
    }

    // ------------------------------------------------------------------
    // 造数/查询 helper
    // ------------------------------------------------------------------

    /** 客户未收回应收余额（与 SalOrderServiceImpl.checkCredit 同口径） */
    private BigDecimal unsettledReceivable() {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount - settled_amount), 0) FROM fin_receivable "
                        + "WHERE customer_id=?",
                BigDecimal.class, customerId);
    }

    private Integer orderStatus(Long orderId) {
        return jdbc.queryForObject(
                "SELECT status FROM sal_order WHERE id=?", Integer.class, orderId);
    }

    /** 单行订单：金额 = qty × price */
    private Long createOrder(BigDecimal qty, BigDecimal price) {
        SalOrderForm form = new SalOrderForm();
        form.setCustomerId(customerId);
        form.setWarehouseId(warehouseId);
        form.setOrderDate(today);
        SalOrderDetailForm line = new SalOrderDetailForm();
        line.setMaterialId(materialId);
        line.setQty(qty);
        line.setPrice(price);
        form.setDetails(List.of(line));
        return orderService.create(form);
    }

    private SalOutstockForm outstockForm(Long orderId, Long orderDetailId, BigDecimal qty) {
        SalOutstockForm form = new SalOutstockForm();
        form.setOrderId(orderId);
        form.setOutDate(today);
        SalOutstockDetailForm line = new SalOutstockDetailForm();
        line.setOrderDetailId(orderDetailId);
        line.setQty(qty);
        form.setDetails(List.of(line));
        return form;
    }

    private SalReturnForm returnForm(Long outstockId, Long outDetailId, BigDecimal qty) {
        SalReturnForm form = new SalReturnForm();
        form.setOutId(outstockId);
        form.setReturnDate(today);
        SalReturnDetailForm line = new SalReturnDetailForm();
        line.setOutDetailId(outDetailId);
        line.setQty(qty);
        form.setDetails(List.of(line));
        return form;
    }
}
