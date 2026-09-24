package com.erp.it;

import com.erp.common.exception.BusinessException;
import com.erp.cost.dto.vo.MaterialBalanceVO;
import com.erp.cost.service.CostService;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 链路二：存货计价台账（CostService → CostLedger）移动加权不变量。
 * <ul>
 *   <li><b>移动加权</b>：100@10 + 100@12 → 单价 11；出库 50 成本 550、结存 150/1650；</li>
 *   <li><b>余额连续性</b>：任一行 balance = 前一行 balance + 本行 qty/amount（台账自洽，
 *       拦截过账顺序错乱 / 并发串行破坏——每次过账经 SELECT...FOR UPDATE 锁最新行）；</li>
 *   <li><b>结存不足拒绝</b>：出库超过计价账面结存直接拒绝（账实不符禁止出库口径）。</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("链路2-移动加权计价与台账连续性")
class CostLedgerIT extends ItTestSupport {

    @Autowired
    private CostService costService;

    private Long warehouseId;
    private Long materialId;
    private final LocalDate today = LocalDate.now();

    @BeforeAll
    void setUpBaseData() {
        warehouseId = newWarehouse("IT成本测试仓-" + uniqueSuffix());
        materialId = newMaterial("IT计价物料-" + uniqueSuffix(), false);
    }

    @Test
    @Order(1)
    @DisplayName("100@10 + 100@12 → 加权单价11；出库50成本550结存150/1650")
    void movingWeightedAverage_pricingAndOut() {
        // 两笔入库计价：金额 1000 + 1200
        costService.onStockIn(materialId, warehouseId, "PUR_IN",
                "IT-COST-IN-1-" + uniqueSuffix(), null, new BigDecimal("100"),
                new BigDecimal("1000"), today);
        costService.onStockIn(materialId, warehouseId, "PUR_IN",
                "IT-COST-IN-2-" + uniqueSuffix(), null, new BigDecimal("100"),
                new BigDecimal("1200"), today);

        // 移动加权单价 = (100×10 + 100×12) / 200 = 11
        MaterialBalanceVO balance = costService.currentBalance(materialId);
        assertThat(balance.qty()).as("结存数量200").isEqualByComparingTo("200");
        assertThat(balance.amount()).as("结存金额2200").isEqualByComparingTo("2200");
        assertThat(balance.unitCost()).as("移动加权单价11").isEqualByComparingTo("11");

        // 纯计算不过账
        assertThat(costService.calcOutCost(materialId, new BigDecimal("50")))
                .as("出库50的预计成本=550").isEqualByComparingTo("550");

        // 过账出库 50
        String outBizNo = "IT-COST-OUT-1-" + uniqueSuffix();
        BigDecimal outCost = costService.onStockOut(materialId, warehouseId, "SAL_OUT",
                outBizNo, null, new BigDecimal("50"), today);
        assertThat(outCost).as("出库成本550").isEqualByComparingTo("550");

        // 台账行：qty=-50、amount=-550、结存 150/1650
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT qty, amount, unit_cost, balance_qty, balance_cost "
                        + "FROM cost_flow WHERE biz_no=?", outBizNo);
        assertThat((BigDecimal) row.get("qty")).isEqualByComparingTo("-50");
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("-550");
        assertThat((BigDecimal) row.get("unit_cost")).isEqualByComparingTo("11");
        assertThat((BigDecimal) row.get("balance_qty")).isEqualByComparingTo("150");
        assertThat((BigDecimal) row.get("balance_cost")).isEqualByComparingTo("1650");

        MaterialBalanceVO after = costService.currentBalance(materialId);
        assertThat(after.qty()).isEqualByComparingTo("150");
        assertThat(after.amount()).isEqualByComparingTo("1650");
    }

    @Test
    @Order(2)
    @DisplayName("台账余额连续性：逐行 balance = 上一行 balance + 本行发生额")
    void ledgerBalance_isContinuousAcrossRows() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT qty, amount, balance_qty, balance_cost FROM cost_flow "
                        + "WHERE material_id=? AND deleted=0 ORDER BY id", materialId);
        assertThat(rows).as("本物料应有3行计价流水").hasSize(3);

        BigDecimal cumQty = BigDecimal.ZERO;
        BigDecimal cumCost = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            cumQty = cumQty.add((BigDecimal) row.get("qty"));
            cumCost = cumCost.add((BigDecimal) row.get("amount"));
            assertThat((BigDecimal) row.get("balance_qty"))
                    .as("第%d行结存数量应等于累计发生额".formatted(rows.indexOf(row) + 1))
                    .isEqualByComparingTo(cumQty);
            assertThat((BigDecimal) row.get("balance_cost"))
                    .as("第%d行结存金额应等于累计发生额".formatted(rows.indexOf(row) + 1))
                    .isEqualByComparingTo(cumCost);
        }
        // 期末自洽：150 / 1650
        assertThat(cumQty).isEqualByComparingTo("150");
        assertThat(cumCost).isEqualByComparingTo("1650");
    }

    @Test
    @Order(3)
    @DisplayName("出库151超过计价结存150：拒绝且台账不新增行")
    void outExceedingBalance_isRejected() {
        int rowsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cost_flow WHERE material_id=? AND deleted=0",
                Integer.class, materialId);

        Throwable tex = catchThrowable(() -> costService.onStockOut(materialId, warehouseId, "SAL_OUT",
                "IT-COST-OUT-2-" + uniqueSuffix(), null, new BigDecimal("151"), today));
        assertBizMsg(tex, "超过计价账面结存");

        int rowsAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cost_flow WHERE material_id=? AND deleted=0",
                Integer.class, materialId);
        assertThat(rowsAfter).as("拒绝的过账不留台账行").isEqualTo(rowsBefore);
    }
}
