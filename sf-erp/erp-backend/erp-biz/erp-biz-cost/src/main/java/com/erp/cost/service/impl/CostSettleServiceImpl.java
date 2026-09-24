package com.erp.cost.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.cost.constant.CostConstants;
import com.erp.cost.dto.raw.MonthlyTotalRaw;
import com.erp.cost.dto.raw.SubjectRaw;
import com.erp.cost.dto.raw.SummaryRaw;
import com.erp.cost.dto.raw.VoucherEntryInsertParam;
import com.erp.cost.dto.raw.VoucherInsertParam;
import com.erp.cost.dto.vo.CostPeriodVO;
import com.erp.cost.dto.vo.MaterialSummaryRowVO;
import com.erp.cost.dto.vo.SettleResultVO;
import com.erp.cost.dto.vo.SettleSummaryVO;
import com.erp.cost.entity.CostPeriod;
import com.erp.cost.mapper.CostFlowMapper;
import com.erp.cost.mapper.CostIntegrationMapper;
import com.erp.cost.mapper.CostPeriodMapper;
import com.erp.cost.service.CostSettleService;
import com.erp.cost.support.CostBizNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 存货月末核算服务实现。
 * <p>
 * 结账事务：行锁 period 行 → 校验状态/顺序 → 期间出库成本汇总（拆销售/生产领料/其他）→
 * 生成转账凭证（借 6401/5001、贷 1405，DRAFT）→ CLOSED 快照。
 * 凭证按 fin_voucher/fin_voucher_entry 约定表 INSERT，不依赖 finance 模块代码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostSettleServiceImpl implements CostSettleService {

    private final CostPeriodMapper periodMapper;
    private final CostFlowMapper flowMapper;
    private final CostIntegrationMapper integrationMapper;
    private final CostBizNoGenerator bizNoGenerator;

    @Override
    public List<CostPeriodVO> periods() {
        return periodMapper.selectList(Wrappers.lambdaQuery(CostPeriod.class)
                        .orderByDesc(CostPeriod::getPeriod))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SettleResultVO settle(String period) {
        String p = requirePeriod(period);
        // 审计日志：结账是该期间计价流水的封版动作（结账后禁过账），操作人必须可追溯
        log.info("[成本结账][开始] 期间={} 操作人={}", p, currentUserId());

        // ---- 1. 期间行：不存在则建（隐含 OPEN），行锁串行化并发结账 ----
        CostPeriod row = periodMapper.selectByPeriodForUpdate(p);
        if (row == null) {
            row = new CostPeriod();
            row.setPeriod(p);
            row.setStatus(CostConstants.PERIOD_OPEN);
            row.setOutAmount(BigDecimal.ZERO);
            periodMapper.insert(row);
            row = periodMapper.selectByPeriodForUpdate(p);
        }
        if (CostConstants.PERIOD_CLOSED.equals(row.getStatus())) {
            log.warn("[成本结账][拒绝] 期间={} 原因=已结账，禁止重复结账", p);
            throw BusinessException.ofKey("erp.cost.period-closed-no-repeat", p);
        }
        // 结账顺序守卫：后续期间已结账说明历史被跳过，拒绝（防止期间倒挂）
        Long laterClosed = periodMapper.selectCount(Wrappers.lambdaQuery(CostPeriod.class)
                .eq(CostPeriod::getStatus, CostConstants.PERIOD_CLOSED)
                .gt(CostPeriod::getPeriod, p));
        if (laterClosed != null && laterClosed > 0) {
            log.warn("[成本结账][拒绝] 期间={} 原因=之后存在已结账期间（期间倒挂）", p);
            throw BusinessException.ofKey("erp.cost.close-in-order", p);
        }

        // ---- 2. 本期出库成本汇总（按业务类型拆分借贷口径） ----
        List<MonthlyTotalRaw> totals = flowMapper.selectMonthlyTotals(p, p);
        BigDecimal outAmount = BigDecimal.ZERO;
        BigDecimal salOutCost = BigDecimal.ZERO;
        BigDecimal prodUseCost = BigDecimal.ZERO;
        for (MonthlyTotalRaw t : totals) {
            outAmount = outAmount.add(nvl(t.getOutAmount()));
            salOutCost = salOutCost.add(nvl(t.getSalOutCost()));
            prodUseCost = prodUseCost.add(nvl(t.getProdUseCost()));
        }
        // 其他出库（OTH_OUT/PUR_RET/ADJUST 盘亏等）也属存货流出，转主营业务成本
        BigDecimal otherOutCost = outAmount.subtract(salOutCost).subtract(prodUseCost);

        // ---- 3. 自动生成转账凭证（出库成本为 0 时跳过，空凭证无记账意义） ----
        Long voucherId = null;
        String voucherNo = null;
        if (outAmount.signum() > 0) {
            Map<String, SubjectRaw> subjects = loadSubjects();
            SubjectRaw mainCost = requireSubject(subjects, CostConstants.SUBJECT_MAIN_COST);
            SubjectRaw prodCost = requireSubject(subjects, CostConstants.SUBJECT_PROD_COST);
            SubjectRaw inventory = requireSubject(subjects, CostConstants.SUBJECT_INVENTORY);

            voucherNo = bizNoGenerator.next(CostBizNoGenerator.VOUCHER);
            VoucherInsertParam voucher = new VoucherInsertParam();
            voucher.setVoucherNo(voucherNo);
            voucher.setVoucherDate(periodEndDate(p));
            voucher.setWord(CostConstants.VOUCHER_WORD_TRANSFER);
            voucher.setTotalDebit(outAmount);
            voucher.setTotalCredit(outAmount);
            voucher.setStatus(CostConstants.VOUCHER_DRAFT);
            voucher.setRemark(p + " 存货月末结账自动生成");
            voucher.setUserId(currentUserId());
            integrationMapper.insertVoucher(voucher);
            voucherId = voucher.getId();

            int line = 1;
            BigDecimal mainCostAmount = salOutCost.add(otherOutCost);
            if (mainCostAmount.signum() > 0) {
                insertEntry(voucherId, line++, "存货核算-" + p + " 销售出库成本结转",
                        mainCost.getId(), mainCostAmount, BigDecimal.ZERO);
            }
            if (prodUseCost.signum() > 0) {
                insertEntry(voucherId, line++, "存货核算-" + p + " 生产领料成本结转",
                        prodCost.getId(), prodUseCost, BigDecimal.ZERO);
            }
            insertEntry(voucherId, line, "存货核算-" + p + " 库存商品出库结转",
                    inventory.getId(), BigDecimal.ZERO, outAmount);
        }

        // ---- 4. 期间 CLOSED + 结账快照 ----
        row.setStatus(CostConstants.PERIOD_CLOSED);
        row.setOutAmount(outAmount);
        row.setVoucherId(voucherId);
        row.setVoucherNo(voucherNo);
        row.setSettleBy(currentUserId());
        row.setSettleTime(java.time.LocalDateTime.now());
        periodMapper.updateById(row);

        // 审计日志：完成行带期间成本汇总与自动凭证号，是财务对账的关键锚点
        log.info("[成本结账][完成] 期间={} 出库成本合计={} 销售出库={} 生产领料={} 其他出库={} 凭证={} 操作人={}",
                p, outAmount.toPlainString(), salOutCost.toPlainString(), prodUseCost.toPlainString(),
                otherOutCost.toPlainString(), voucherNo == null ? "无(出库成本为0)" : voucherNo, currentUserId());
        return new SettleResultVO(p, row.getStatus(), outAmount, salOutCost, prodUseCost,
                otherOutCost, voucherId, voucherNo);
    }

    @Override
    public SettleSummaryVO summary(String period) {
        String p = requirePeriod(period);
        List<SummaryRaw> raws = flowMapper.selectPeriodSummary(p);
        List<MaterialSummaryRowVO> rows = raws.stream().map(r -> {
            BigDecimal beginQty = nvl(r.getBeginQty());
            BigDecimal beginAmount = nvl(r.getBeginAmount());
            BigDecimal inQty = nvl(r.getInQty());
            BigDecimal inAmount = nvl(r.getInAmount());
            BigDecimal outQty = nvl(r.getOutQty());
            BigDecimal outAmount = nvl(r.getOutAmount());
            return new MaterialSummaryRowVO(r.getMaterialId(), r.getMaterialCode(), r.getMaterialName(),
                    beginQty, beginAmount, inQty, inAmount, outQty, outAmount,
                    beginQty.add(inQty).subtract(outQty), beginAmount.add(inAmount).subtract(outAmount));
        }).toList();

        BigDecimal totalBegin = BigDecimal.ZERO;
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        BigDecimal totalEnd = BigDecimal.ZERO;
        for (MaterialSummaryRowVO row : rows) {
            totalBegin = totalBegin.add(row.beginAmount());
            totalIn = totalIn.add(row.inAmount());
            totalOut = totalOut.add(row.outAmount());
            totalEnd = totalEnd.add(row.endAmount());
        }
        String status = periodMapper.selectCount(Wrappers.lambdaQuery(CostPeriod.class)
                        .eq(CostPeriod::getPeriod, p)
                        .eq(CostPeriod::getStatus, CostConstants.PERIOD_CLOSED)) > 0
                ? CostConstants.PERIOD_CLOSED : CostConstants.PERIOD_OPEN;
        return new SettleSummaryVO(p, status, rows, totalBegin, totalIn, totalOut, totalEnd);
    }

    private void insertEntry(Long voucherId, int lineNo, String summary, Long subjectId,
                             BigDecimal debit, BigDecimal credit) {
        VoucherEntryInsertParam entry = new VoucherEntryInsertParam();
        entry.setVoucherId(voucherId);
        entry.setLineNo(lineNo);
        entry.setSummary(summary);
        entry.setSubjectId(subjectId);
        entry.setDebitAmount(debit);
        entry.setCreditAmount(credit);
        entry.setUserId(currentUserId());
        integrationMapper.insertVoucherEntry(entry);
    }

    private Map<String, SubjectRaw> loadSubjects() {
        List<SubjectRaw> subjects = integrationMapper.selectSubjects(List.of(
                CostConstants.SUBJECT_MAIN_COST, CostConstants.SUBJECT_PROD_COST,
                CostConstants.SUBJECT_INVENTORY));
        return subjects.stream().collect(Collectors.toMap(SubjectRaw::getCode, Function.identity()));
    }

    private SubjectRaw requireSubject(Map<String, SubjectRaw> subjects, String code) {
        SubjectRaw s = subjects.get(code);
        if (s == null) {
            throw BusinessException.ofKey("erp.cost.close-failed-subject-missing", code);
        }
        return s;
    }

    private CostPeriodVO toVO(CostPeriod row) {
        return new CostPeriodVO(row.getId(), row.getPeriod(), row.getStatus(), row.getOutAmount(),
                row.getVoucherId(), row.getVoucherNo(), row.getSettleBy(), row.getSettleTime(),
                row.getRemark());
    }

    /** 当前登录用户 ID（JWT principal 约定，未登录返回 0=系统） */
    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return 0L;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String requirePeriod(String period) {
        if (period == null || period.isBlank()) {
            throw BusinessException.ofKey("erp.cost.period-required");
        }
        try {
            YearMonth.parse(period.trim(), CostConstants.PERIOD_FORMATTER);
        } catch (DateTimeParseException e) {
            throw BusinessException.ofKey("erp.cost.period-invalid", period);
        }
        return period.trim();
    }

    /** 期间最后一日（凭证日期） */
    private LocalDate periodEndDate(String period) {
        YearMonth ym = YearMonth.parse(period, CostConstants.PERIOD_FORMATTER);
        return ym.atEndOfMonth();
    }
}
