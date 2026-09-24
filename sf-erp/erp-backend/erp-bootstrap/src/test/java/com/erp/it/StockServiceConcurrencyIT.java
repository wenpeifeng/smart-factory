package com.erp.it;

import com.erp.common.exception.BusinessException;
import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.constant.OutStrategy;
import com.erp.inventory.service.StockService;
import com.erp.inventory.service.StockService.DeductionLeg;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 链路一：库存守门人（StockService）并发与 FIFO 不变量。
 * <ul>
 *   <li><b>库存永不透支</b>：扣减走条件 UPDATE（行锁 + qty+delta>=0），
 *       并发扣减只允许"库存够"的请求成功，其余一律按"库存不足"回滚；</li>
 *   <li><b>每次变动必有流水</b>：成功几笔=几条 movement，失败的随事务回滚不留痕；</li>
 *   <li><b>FIFO 拆批</b>：跨批出库按 first_in_at 升序逐批扣减，一批次一行流水，
 *       同一 bizNo 串联，总量不足整体回滚。</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("链路1-库存行锁防负库存 + FIFO拆批")
class StockServiceConcurrencyIT extends ItTestSupport {

    @Autowired
    private StockService stockService;

    private Long warehouseId;
    private Long materialId;

    @BeforeAll
    void setUpBaseData() {
        warehouseId = newWarehouse("IT库存测试仓-" + uniqueSuffix());
        materialId = newMaterial("IT非批次物料-" + uniqueSuffix(), false);
    }

    @Test
    @Order(1)
    @DisplayName("10线程并发各扣15（初始35）：恰好2成功8拒，终态5无负数")
    void concurrentDeduct_neverOversells() throws Exception {
        // 初始库存 35（走业务入口入库，同时验证 UPSERT 首插路径）
        stockService.changeStock(warehouseId, materialId, new BigDecimal("35"),
                InvBizType.OTH_IN, null, "IT-STK-INIT-" + uniqueSuffix());

        int threads = 10;
        BigDecimal each = new BigDecimal("15");
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            final String bizNo = "IT-STK-CONC-" + uniqueSuffix() + "-" + i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    stockService.changeStock(warehouseId, materialId, each.negate(),
                            InvBizType.SAL_OUT, null, bizNo);
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    // 预期内的唯一失败形态："库存不足"
                    if ((e.getMessageKey() == null || !e.getMessageKey().startsWith("erp.inv.stock-not-enough"))
                            && (e.getMessage() == null || !e.getMessage().contains("库存不足"))) {
                        unexpected.add(e);
                    }
                    rejected.incrementAndGet();
                } catch (Throwable t) {
                    unexpected.add(t);
                }
            });
        }
        // 同发起令：所有线程同时开抢
        assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(unexpected).isEmpty();
        assertThat(success.get()).as("恰好 2 笔扣减成功（35/15=2 余 5）").isEqualTo(2);
        assertThat(rejected.get()).as("其余 8 笔被'库存不足'拒绝").isEqualTo(8);

        // 终态：qty=5，永不透支
        BigDecimal qty = summaryQty(warehouseId, materialId, "");
        assertThat(qty).as("终态库存=5").isEqualByComparingTo("5");
        assertThat(qty.signum()).as("库存非负").isGreaterThanOrEqualTo(0);

        // 流水守恒：成功的 2 笔各留一条 SAL_OUT 负流水，合计 -30；失败笔不留痕
        Integer outMovements = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inv_movement WHERE warehouse_id=? AND material_id=? "
                        + "AND biz_type='SAL_OUT' AND biz_no LIKE 'IT-STK-CONC-%'",
                Integer.class, warehouseId, materialId);
        assertThat(outMovements).as("仅成功的 2 笔留流水").isEqualTo(2);
        BigDecimal movementSum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(qty), 0) FROM inv_movement WHERE warehouse_id=? AND material_id=? "
                        + "AND biz_type='SAL_OUT' AND biz_no LIKE 'IT-STK-CONC-%'",
                BigDecimal.class, warehouseId, materialId);
        assertThat(movementSum).as("出库流水合计=-30").isEqualByComparingTo("-30");
    }

    @Test
    @Order(2)
    @DisplayName("FIFO跨批出库80（B1=60,B2=40）：先扣老批，movement拆2行同单号")
    void fifoDeduct_splitsMovementsAcrossBatches() throws Exception {
        Long batchMaterialId = newMaterial("IT批次物料-" + uniqueSuffix(), true);

        // 两个批次先后入库（间隔>1s 保证 first_in_at 严格有序，不依赖 id 兜底）
        stockService.stockIn(warehouseId, batchMaterialId, "B1", new BigDecimal("60"),
                InvBizType.OTH_IN, null, "IT-FIFO-IN-1-" + uniqueSuffix());
        Thread.sleep(1100);
        stockService.stockIn(warehouseId, batchMaterialId, "B2", new BigDecimal("40"),
                InvBizType.OTH_IN, null, "IT-FIFO-IN-2-" + uniqueSuffix());

        String outBizNo = "IT-FIFO-OUT-1-" + uniqueSuffix();
        List<DeductionLeg> legs = stockService.stockOut(warehouseId, batchMaterialId,
                new BigDecimal("80"), OutStrategy.FIFO, null,
                InvBizType.SAL_OUT, null, outBizNo, null);

        // 拆批明细：老批 B1 全部扣完 + 新批 B2 补足差额
        assertThat(legs).hasSize(2);
        assertThat(legs.get(0).batchNo()).isEqualTo("B1");
        assertThat(legs.get(0).qty()).isEqualByComparingTo("60");
        assertThat(legs.get(1).batchNo()).isEqualTo("B2");
        assertThat(legs.get(1).qty()).isEqualByComparingTo("20");

        // movement 拆行：一批次一行，同一 bizNo，数量正入负出
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT batch_no, qty FROM inv_movement WHERE biz_no=? ORDER BY id", outBizNo);
        assertThat(rows).hasSize(2);
        assertThat(((String) rows.get(0).get("batch_no"))).isEqualTo("B1");
        assertThat(((BigDecimal) rows.get(0).get("qty"))).isEqualByComparingTo("-60");
        assertThat(((String) rows.get(1).get("batch_no"))).isEqualTo("B2");
        assertThat(((BigDecimal) rows.get(1).get("qty"))).isEqualByComparingTo("-20");

        // 批次余额：B1=0，B2=20
        assertThat(summaryQty(warehouseId, batchMaterialId, "B1")).isEqualByComparingTo("0");
        assertThat(summaryQty(warehouseId, batchMaterialId, "B2")).isEqualByComparingTo("20");
    }

    @Test
    @Order(3)
    @DisplayName("FIFO出库超过全部批次总量：拒绝且不留部分流水")
    void fifoDeduct_insufficientTotal_rejectedAtomically() {
        Long batchMaterialId = newMaterial("IT批次物料2-" + uniqueSuffix(), true);
        stockService.stockIn(warehouseId, batchMaterialId, "B1", new BigDecimal("10"),
                InvBizType.OTH_IN, null, "IT-FIFO2-IN-1-" + uniqueSuffix());
        stockService.stockIn(warehouseId, batchMaterialId, "B2", new BigDecimal("10"),
                InvBizType.OTH_IN, null, "IT-FIFO2-IN-2-" + uniqueSuffix());

        String outBizNo = "IT-FIFO2-OUT-1-" + uniqueSuffix();
        Throwable tex = catchThrowable(() -> stockService.stockOut(warehouseId, batchMaterialId,
                        new BigDecimal("25"), OutStrategy.FIFO, null,
                        InvBizType.SAL_OUT, null, outBizNo, null));
        assertBizMsg(tex, "库存不足");

        // 整体回滚：无流水、批次余量原封不动
        Integer movements = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inv_movement WHERE biz_no=?", Integer.class, outBizNo);
        assertThat(movements).as("拒绝的出库不留任何流水").isZero();
        assertThat(summaryQty(warehouseId, batchMaterialId, "B1")).isEqualByComparingTo("10");
        assertThat(summaryQty(warehouseId, batchMaterialId, "B2")).isEqualByComparingTo("10");
    }

    /** 查指定（仓库,物料,批次）行的现存量 */
    private BigDecimal summaryQty(Long warehouseId, Long materialId, String batchNo) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(qty), 0) FROM inv_stock "
                        + "WHERE warehouse_id=? AND material_id=? AND batch_no=? AND deleted=0",
                BigDecimal.class, warehouseId, materialId, batchNo);
    }
}
