package com.erp.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.dto.query.ScoreSourceRow;
import com.erp.basedata.dto.vo.SupplierGradeSummaryVO;
import com.erp.basedata.dto.vo.SupplierScoreVO;
import com.erp.basedata.entity.BaseSupplier;
import com.erp.basedata.entity.PurSupplierScore;
import com.erp.basedata.mapper.BaseSupplierMapper;
import com.erp.basedata.mapper.PurSupplierScoreMapper;
import com.erp.basedata.mapper.SupplierScoreSourceMapper;
import com.erp.basedata.service.PurSupplierScoreService;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 供应商交易评分 Service 实现（V141）。
 * <p>
 * <b>评分算法</b>（详见 docs/modules/supplier-admit-rating.md）：
 * <ul>
 *   <li>准时交货率 ontimeRate = 准时入库单数 / 有交期入库单数 ×100
 *       （入库日期 ≤ 订单明细最早要求交期；无交期单不参与）</li>
 *   <li>来料合格率 qualifiedRate = IQC 合格数 / (合格数+不合格数) ×100
 *       （qc_inspect：status=10、source_type=pur_instock）</li>
 *   <li>退货率 returnRate = 期间退货数量 / 期间入库数量 ×100（封顶 100，无入库=0）</li>
 *   <li>总分 = 100 × (W_ONTIME×准时率 + W_QUAL×合格率 + W_RET×(1-退货率))，
 *       权重常量 0.30 / 0.40 / 0.30，HALF_UP 2 位</li>
 *   <li>等级：A≥90 / B≥75 / C≥60 / D&lt;60</li>
 * </ul>
 * 重算 = 先删该 (periodType, period) 全部行再插入（uk 兜底防并发重号）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurSupplierScoreServiceImpl implements PurSupplierScoreService {

    /** 权重：准时交货率（三项之和须为 1） */
    public static final BigDecimal W_ONTIME = new BigDecimal("0.30");
    /** 权重：来料合格率 */
    public static final BigDecimal W_QUALIFIED = new BigDecimal("0.40");
    /** 权重：退货率（逆向，取 1-退货率） */
    public static final BigDecimal W_RETURN = new BigDecimal("0.30");

    /** 等级阈值（含）：A≥90 / B≥75 / C≥60 */
    private static final BigDecimal GRADE_A = new BigDecimal("90");
    private static final BigDecimal GRADE_B = new BigDecimal("75");
    private static final BigDecimal GRADE_C = new BigDecimal("60");

    private static final String PERIOD_MONTH = "MONTH";
    private static final String PERIOD_QUARTER = "QUARTER";

    private final PurSupplierScoreMapper scoreMapper;
    private final SupplierScoreSourceMapper sourceMapper;
    private final BaseSupplierMapper supplierMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recalc(String periodType, String period) {
        String type = normalizeType(periodType);
        YearMonth month = resolvePeriod(type, period);
        LocalDate start;
        LocalDate end;
        String periodLabel;
        if (PERIOD_QUARTER.equals(type)) {
            int q = (month.getMonthValue() - 1) / 3 + 1;
            start = LocalDate.of(month.getYear(), (q - 1) * 3 + 1, 1);
            end = LocalDate.of(start.getYear(), start.getMonthValue() + 2, 1)
                    .minusDays(1);
            periodLabel = "%d-Q%d".formatted(month.getYear(), q);
        } else {
            start = LocalDate.of(month.getYear(), month.getMonthValue(), 1);
            end = start.plusMonths(1).minusDays(1);
            periodLabel = "%d-%02d".formatted(month.getYear(), month.getMonthValue());
        }

        // 四类来源按 supplierId 聚合后在内存合并
        Map<Long, ScoreSourceRow> merged = new HashMap<>();
        for (List<ScoreSourceRow> rows : List.of(
                sourceMapper.sumOrders(start, end),
                sourceMapper.sumInstocks(start, end),
                sourceMapper.sumQcInspects(start, end),
                sourceMapper.sumReturns(start, end))) {
            for (ScoreSourceRow row : rows) {
                merged.merge(row.getSupplierId(), row, PurSupplierScoreServiceImpl::mergeRow);
            }
        }

        // 重算 = 先删后插（含零数据供应商的历史行清理）
        scoreMapper.delete(Wrappers.lambdaQuery(PurSupplierScore.class)
                .eq(PurSupplierScore::getPeriodType, type)
                .eq(PurSupplierScore::getPeriod, periodLabel));

        LocalDateTime now = LocalDateTime.now();
        for (ScoreSourceRow row : merged.values()) {
            PurSupplierScore score = buildScore(row, type, periodLabel, start, end, now);
            scoreMapper.insert(score);
        }
        log.info("[供应商评分] {} {} 重算完成，参与供应商 {} 家", type, periodLabel, merged.size());
        return merged.size();
    }

    @Override
    public PageResult<SupplierScoreVO> page(long pageNum, long pageSize, Long supplierId,
                                            String periodType, String period, String grade) {
        var wrapper = Wrappers.lambdaQuery(PurSupplierScore.class)
                .eq(supplierId != null, PurSupplierScore::getSupplierId, supplierId)
                .eq(periodType != null && !periodType.isBlank(), PurSupplierScore::getPeriodType, periodType)
                .eq(period != null && !period.isBlank(), PurSupplierScore::getPeriod, period)
                .eq(grade != null && !grade.isBlank(), PurSupplierScore::getGrade, grade)
                .orderByDesc(PurSupplierScore::getPeriodType)
                .orderByDesc(PurSupplierScore::getPeriod)
                .orderByDesc(PurSupplierScore::getTotalScore);
        PageResult<SupplierScoreVO> page = PageUtils.of(scoreMapper, wrapper, pageNum, pageSize, SupplierScoreVO::from);
        fillNames(page.getRecords());
        return page;
    }

    @Override
    public SupplierGradeSummaryVO summary(String periodType, String period) {
        String type = normalizeType(periodType);
        SupplierGradeSummaryVO vo = new SupplierGradeSummaryVO();
        vo.setPeriodType(type);
        List<PurSupplierScore> rows;
        if (period != null && !period.isBlank()) {
            vo.setPeriod(period);
            rows = scoreMapper.selectList(Wrappers.lambdaQuery(PurSupplierScore.class)
                    .eq(PurSupplierScore::getPeriodType, type)
                    .eq(PurSupplierScore::getPeriod, period));
        } else {
            // 最近一个有数据的周期
            PurSupplierScore latest = scoreMapper.selectOne(Wrappers.lambdaQuery(PurSupplierScore.class)
                    .eq(PurSupplierScore::getPeriodType, type)
                    .orderByDesc(PurSupplierScore::getPeriod)
                    .last("LIMIT 1"));
            if (latest == null) {
                vo.setAvgScore(BigDecimal.ZERO);
                vo.setTotalOrderAmount(BigDecimal.ZERO);
                return vo;
            }
            vo.setPeriod(latest.getPeriod());
            rows = scoreMapper.selectList(Wrappers.lambdaQuery(PurSupplierScore.class)
                    .eq(PurSupplierScore::getPeriodType, type)
                    .eq(PurSupplierScore::getPeriod, latest.getPeriod()));
        }
        vo.setSupplierCount(rows.size());
        for (PurSupplierScore row : rows) {
            switch (row.getGrade() == null ? "D" : row.getGrade()) {
                case "A" -> vo.setGradeA(vo.getGradeA() + 1);
                case "B" -> vo.setGradeB(vo.getGradeB() + 1);
                case "C" -> vo.setGradeC(vo.getGradeC() + 1);
                default -> vo.setGradeD(vo.getGradeD() + 1);
            }
        }
        if (!rows.isEmpty()) {
            BigDecimal sum = rows.stream().map(PurSupplierScore::getTotalScore)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            vo.setAvgScore(sum.divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP));
            vo.setTotalOrderAmount(rows.stream().map(PurSupplierScore::getOrderAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        } else {
            vo.setAvgScore(BigDecimal.ZERO);
            vo.setTotalOrderAmount(BigDecimal.ZERO);
        }
        return vo;
    }

    /* ===================== 私有支撑 ===================== */

    /** 合并两行（同一 supplierId 不同来源） */
    private static ScoreSourceRow mergeRow(ScoreSourceRow a, ScoreSourceRow b) {
        if (a.getOrderCount() != null) {
            a.setOrderCount(a.getOrderCount());
        } else if (b.getOrderCount() != null) {
            a.setOrderCount(b.getOrderCount());
            a.setOrderAmount(b.getOrderAmount());
        }
        if (b.getInstockCount() != null) {
            a.setInstockCount(b.getInstockCount());
            a.setDueCount(b.getDueCount());
            a.setOntimeCount(b.getOntimeCount());
            a.setInstockQty(b.getInstockQty());
        }
        if (b.getQualifiedQty() != null) {
            a.setQualifiedQty(b.getQualifiedQty());
            a.setUnqualifiedQty(b.getUnqualifiedQty());
        }
        if (b.getReturnQty() != null) {
            a.setReturnQty(b.getReturnQty());
        }
        return a;
    }

    private PurSupplierScore buildScore(ScoreSourceRow row, String type, String periodLabel,
                                        LocalDate start, LocalDate end, LocalDateTime now) {
        int orderCount = row.getOrderCount() == null ? 0 : row.getOrderCount();
        BigDecimal orderAmount = nvl(row.getOrderAmount());
        int instockCount = row.getInstockCount() == null ? 0 : row.getInstockCount();
        BigDecimal instockQty = nvl(row.getInstockQty());

        // 准时交货率：分母 0（无带交期的入库单）记 100%（视为不拖累），口径见文档
        int dueCount = row.getDueCount() == null ? 0 : row.getDueCount();
        int ontimeCount = row.getOntimeCount() == null ? 0 : row.getOntimeCount();
        BigDecimal ontimeRate = dueCount == 0 ? bd(100)
                : bd(ontimeCount * 100.0 / dueCount);

        // 来料合格率：无检验记录记 100%（未检不扣分），口径见文档
        BigDecimal qualifiedQty = nvl(row.getQualifiedQty());
        BigDecimal unqualifiedQty = nvl(row.getUnqualifiedQty());
        BigDecimal inspected = qualifiedQty.add(unqualifiedQty);
        BigDecimal qualifiedRate = inspected.compareTo(BigDecimal.ZERO) == 0 ? bd(100)
                : qualifiedQty.multiply(bd(100)).divide(inspected, 2, RoundingMode.HALF_UP);

        // 退货率：无入库记 0（无法定义），封顶 100
        BigDecimal returnQty = nvl(row.getReturnQty());
        BigDecimal returnRate = BigDecimal.ZERO;
        if (instockQty.compareTo(BigDecimal.ZERO) > 0) {
            returnRate = returnQty.multiply(bd(100)).divide(instockQty, 2, RoundingMode.HALF_UP)
                    .min(bd(100));
        }

        // 总分 = 100 × (30%×准时率 + 40%×合格率 + 30%×(1-退货率))，比率转小数
        BigDecimal score = W_ONTIME.multiply(ontimeRate)
                .add(W_QUALIFIED.multiply(qualifiedRate))
                .add(W_RETURN.multiply(bd(100).subtract(returnRate)))
                .setScale(2, RoundingMode.HALF_UP);

        PurSupplierScore entity = new PurSupplierScore();
        entity.setSupplierId(row.getSupplierId());
        entity.setPeriodType(type);
        entity.setPeriod(periodLabel);
        entity.setPeriodStart(start);
        entity.setPeriodEnd(end);
        entity.setOrderCount(orderCount);
        entity.setOrderAmount(orderAmount);
        entity.setInstockCount(instockCount);
        entity.setInstockQty(instockQty);
        entity.setOntimeRate(ontimeRate);
        entity.setQualifiedRate(qualifiedRate);
        entity.setReturnRate(returnRate);
        entity.setTotalScore(score);
        entity.setGrade(gradeOf(score));
        entity.setCalcTime(now);
        return entity;
    }

    private static String gradeOf(BigDecimal score) {
        if (score.compareTo(GRADE_A) >= 0) {
            return "A";
        }
        if (score.compareTo(GRADE_B) >= 0) {
            return "B";
        }
        if (score.compareTo(GRADE_C) >= 0) {
            return "C";
        }
        return "D";
    }

    private String normalizeType(String periodType) {
        if (periodType == null || periodType.isBlank()) {
            return PERIOD_MONTH;
        }
        if (PERIOD_MONTH.equalsIgnoreCase(periodType)) {
            return PERIOD_MONTH;
        }
        if (PERIOD_QUARTER.equalsIgnoreCase(periodType)) {
            return PERIOD_QUARTER;
        }
        throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.biz.sup-score-period-type-invalid", periodType);
    }

    /** 周期标识解析为起始月份：空 = 最近一个已完结周期（上月 / 上季度） */
    private YearMonth resolvePeriod(String type, String period) {
        YearMonth now = YearMonth.now();
        if (period == null || period.isBlank()) {
            if (PERIOD_QUARTER.equals(type)) {
                // 当前季度已完结的第一个月往前推一个季度，取上季度首月
                int curQ = (now.getMonthValue() - 1) / 3;
                return now.minusMonths(curQ + 3L);
            }
            return now.minusMonths(1);
        }
        try {
            if (PERIOD_QUARTER.equals(type)) {
                // yyyy-Qn → 该季度首月
                String[] parts = period.split("-Q");
                int year = Integer.parseInt(parts[0]);
                int q = Integer.parseInt(parts[1]);
                if (q < 1 || q > 4) {
                    throw new IllegalArgumentException("quarter");
                }
                return YearMonth.of(year, (q - 1) * 3 + 1);
            }
            // yyyy-MM
            String[] parts = period.split("-");
            return YearMonth.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception e) {
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.biz.sup-score-period-invalid", period);
        }
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(int v) {
        return BigDecimal.valueOf(v);
    }

    /** 列表补供应商编码/名称 */
    private void fillNames(List<SupplierScoreVO> records) {
        if (records.isEmpty()) {
            return;
        }
        Map<Long, BaseSupplier> suppliers = supplierMapper.selectBatchIds(
                        records.stream().map(SupplierScoreVO::getSupplierId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BaseSupplier::getId, Function.identity(), (a, b) -> a));
        List<SupplierScoreVO> missing = new ArrayList<>();
        for (SupplierScoreVO vo : records) {
            BaseSupplier supplier = suppliers.get(vo.getSupplierId());
            if (supplier != null) {
                vo.setSupplierCode(supplier.getCode());
                vo.setSupplierName(supplier.getName());
            } else {
                missing.add(vo);
            }
        }
        for (SupplierScoreVO vo : missing) {
            vo.setSupplierName("(供应商已删除 id=" + vo.getSupplierId() + ")");
        }
    }
}
