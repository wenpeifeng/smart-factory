package com.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.finance.dto.form.VoucherForm;
import com.erp.finance.dto.request.VoucherQuery;
import com.erp.finance.dto.vo.SubjectAggregateRawVO;
import com.erp.finance.dto.vo.SubjectSummaryVO;
import com.erp.finance.dto.vo.TrialBalanceRawVO;
import com.erp.finance.dto.vo.TrialBalanceRowVO;
import com.erp.finance.dto.vo.TrialBalanceVO;
import com.erp.finance.dto.vo.VoucherDetailVO;
import com.erp.finance.dto.vo.VoucherEntryVO;
import com.erp.finance.dto.vo.VoucherVO;
import com.erp.finance.context.BookContext;
import com.erp.finance.entity.FinSubject;
import com.erp.finance.entity.FinVoucher;
import com.erp.finance.entity.FinVoucherEntry;
import com.erp.finance.mapper.FinQueryMapper;
import com.erp.finance.mapper.FinSubjectMapper;
import com.erp.finance.mapper.FinVoucherEntryMapper;
import com.erp.finance.mapper.FinVoucherMapper;
import com.erp.finance.service.FinPeriodService;
import com.erp.finance.service.FinVoucherService;
import com.erp.finance.support.FinanceBizNoGenerator;
import com.erp.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 总账凭证服务实现。
 * <p>
 * 借贷平衡用 BigDecimal.compareTo 精确比较（DECIMAL(18,2)，无浮点误差）；
 * 状态机单向 DRAFT → POSTED，过账（审核）后的凭证禁改禁删，
 * 科目汇总/试算平衡只统计 POSTED 凭证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinVoucherServiceImpl implements FinVoucherService {

    /** 凭证状态：草稿（可改可删） */
    private static final String STATUS_DRAFT = "DRAFT";
    /** 凭证状态：已过账（禁改禁删） */
    private static final String STATUS_POSTED = "POSTED";

    private final FinVoucherMapper voucherMapper;
    private final FinVoucherEntryMapper entryMapper;
    private final FinSubjectMapper subjectMapper;
    private final FinQueryMapper queryMapper;
    private final FinanceBizNoGenerator bizNoGenerator;
    private final FinPeriodService periodService;

    @Override
    public PageResult<VoucherVO> page(VoucherQuery query, long pageNum, long pageSize) {
        var wrapper = Wrappers.lambdaQuery(FinVoucher.class)
                .eq(FinVoucher::getBookId, BookContext.getBookId())
                .like(notBlank(query.keyword()), FinVoucher::getVoucherNo, trim(query.keyword()))
                .eq(notBlank(query.status()), FinVoucher::getStatus, trim(query.status()));
        LocalDate from = parseDate(query.from());
        LocalDate to = parseDate(query.to());
        if (from != null) {
            wrapper.ge(FinVoucher::getVoucherDate, from);
        }
        if (to != null) {
            wrapper.le(FinVoucher::getVoucherDate, to);
        }
        wrapper.orderByDesc(FinVoucher::getVoucherDate).orderByDesc(FinVoucher::getId);
        return PageUtils.of(voucherMapper, wrapper, pageNum, pageSize, VoucherVO::from);
    }

    @Override
    public VoucherDetailVO detail(Long id) {
        FinVoucher voucher = requireVoucher(id);
        List<FinVoucherEntry> entries = entryMapper.selectList(
                Wrappers.lambdaQuery(FinVoucherEntry.class)
                        .eq(FinVoucherEntry::getVoucherId, id)
                        .orderByAsc(FinVoucherEntry::getLineNo));
        Map<Long, FinSubject> subjects = subjectMap(
                entries.stream().map(FinVoucherEntry::getSubjectId).toList());
        List<VoucherEntryVO> entryVOs = entries.stream().map(e -> {
            FinSubject s = subjects.get(e.getSubjectId());
            return new VoucherEntryVO(e.getLineNo(), e.getSummary(), e.getSubjectId(),
                    s == null ? null : s.getCode(), s == null ? null : s.getName(),
                    e.getDebitAmount(), e.getCreditAmount());
        }).toList();
        return new VoucherDetailVO(voucher.getId(), voucher.getVoucherNo(), voucher.getVoucherDate(),
                voucher.getWord(), voucher.getAttachmentCount(), voucher.getTotalDebit(),
                voucher.getTotalCredit(), voucher.getStatus(), voucher.getSourceType(),
                voucher.getSourceId(), voucher.getRemark(),
                voucher.getCreateTime(), entryVOs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(VoucherForm form) {
        VoucherTotals totals = validateEntries(form.entries());
        // 期间控制（V521）：凭证日期所在月已结账则拒绝新增
        periodService.assertPeriodOpen(form.voucherDate(), BookContext.getBookId(), "新增凭证");
        FinVoucher voucher = new FinVoucher();
        voucher.setBookId(BookContext.getBookId());
        voucher.setVoucherNo(bizNoGenerator.next(FinanceBizNoGenerator.VOUCHER));
        voucher.setVoucherDate(form.voucherDate());
        voucher.setWord(form.word());
        voucher.setAttachmentCount(form.attachmentCount() == null ? 0 : form.attachmentCount());
        voucher.setTotalDebit(totals.debit());
        voucher.setTotalCredit(totals.credit());
        voucher.setStatus(STATUS_DRAFT);
        voucher.setRemark(form.remark());
        voucherMapper.insert(voucher);
        insertEntries(voucher.getId(), form.entries());
        // 审计日志：凭证是总账唯一入账依据，号/日期/借贷合计必须可检索
        log.info("[凭证][新增] 凭证号={} 日期={} 分录数={} 借方合计={} 贷方合计={}",
                voucher.getVoucherNo(), voucher.getVoucherDate(), form.entries().size(),
                totals.debit().toPlainString(), totals.credit().toPlainString());
        return voucher.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, VoucherForm form) {
        FinVoucher voucher = requireEditableVoucher(id);
        VoucherTotals totals = validateEntries(form.entries());
        // 期间控制（V521）：修改前后的凭证日期所在月均须未结账（改期跨入已结账月同样拒绝）
        periodService.assertPeriodOpen(voucher.getVoucherDate(), BookContext.getBookId(), "修改凭证");
        periodService.assertPeriodOpen(form.voucherDate(), BookContext.getBookId(), "修改凭证");
        // 分录整体重写：旧分录逻辑删除后按新表单插入
        entryMapper.delete(Wrappers.lambdaQuery(FinVoucherEntry.class)
                .eq(FinVoucherEntry::getVoucherId, id));
        voucher.setVoucherDate(form.voucherDate());
        voucher.setWord(form.word());
        voucher.setAttachmentCount(form.attachmentCount() == null ? 0 : form.attachmentCount());
        voucher.setTotalDebit(totals.debit());
        voucher.setTotalCredit(totals.credit());
        voucher.setRemark(form.remark());
        voucherMapper.updateById(voucher);
        insertEntries(id, form.entries());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinVoucher voucher = requireEditableVoucher(id);
        // 期间控制（V521）：凭证日期所在月已结账则拒绝删除
        periodService.assertPeriodOpen(voucher.getVoucherDate(), BookContext.getBookId(), "删除凭证");
        // 主表与分录一并逻辑删除
        entryMapper.delete(Wrappers.lambdaQuery(FinVoucherEntry.class)
                .eq(FinVoucherEntry::getVoucherId, id));
        voucherMapper.deleteById(id);
    }

    @Override
    public void audit(Long id) {
        FinVoucher voucher = requireVoucher(id);
        if (STATUS_POSTED.equals(voucher.getStatus())) {
            throw BusinessException.ofKey("erp.fin.voucher-posted-no-reaudit");
        }
        // 期间控制（V521）：凭证日期所在月已结账则拒绝过账
        periodService.assertPeriodOpen(voucher.getVoucherDate(), BookContext.getBookId(), "过账凭证");
        voucher.setStatus(STATUS_POSTED);
        voucher.setAuditTime(LocalDateTime.now());
        // audit_by 待 JWT 用户上下文接入后补充（当前由 MP 审计填充 update_by）
        voucherMapper.updateById(voucher);
        // 审计日志：过账后凭证禁改禁删且参与试算平衡，谁过账的必须留痕（操作人见 MDC userId）
        log.info("[凭证][过账] 凭证号={} 日期={} 借方合计={} 贷方合计={}",
                voucher.getVoucherNo(), voucher.getVoucherDate(),
                voucher.getTotalDebit().toPlainString(), voucher.getTotalCredit().toPlainString());
    }

    @Override
    public SubjectSummaryVO summary(Long subjectId, LocalDate from, LocalDate to) {
        LocalDate rangeFrom = from == null ? LocalDate.now().withDayOfYear(1) : from;
        LocalDate rangeTo = to == null ? LocalDate.now().withDayOfYear(1).plusYears(1).minusDays(1) : to;

        FinSubject subject = null;
        if (subjectId != null) {
            subject = subjectMapper.selectById(subjectId);
            if (subject == null) {
                throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.subject-not-exists", subjectId);
            }
            // 科目汇总只统计当前账套的科目，跨账套 ID 视同不存在
            BookContext.checkBookOwnership(subject.getBookId(),
                    "科目[%s %s]".formatted(subject.getCode(), subject.getName()));
        }
        Long bookId = BookContext.getBookId();
        SubjectAggregateRawVO raw = nz(queryMapper.selectSubjectAggregate(bookId, subjectId, rangeFrom, rangeTo));
        // 余额按科目方向取正：借方科目 = 借 - 贷；贷方科目 = 贷 - 借
        boolean creditDirection = subject != null && "CREDIT".equals(subject.getDirection());
        BigDecimal opening = creditDirection
                ? raw.openingCreditSum().subtract(raw.openingDebitSum())
                : raw.openingDebitSum().subtract(raw.openingCreditSum());
        BigDecimal closing = opening.add(creditDirection
                ? raw.periodCreditSum().subtract(raw.periodDebitSum())
                : raw.periodDebitSum().subtract(raw.periodCreditSum()));
        return new SubjectSummaryVO(subjectId,
                subject == null ? null : subject.getCode(),
                subject == null ? "(全部科目合计)" : subject.getName(),
                subject == null ? null : subject.getDirection(),
                opening, raw.periodDebitSum(), raw.periodCreditSum(), closing);
    }

    @Override
    public TrialBalanceVO trialBalance(LocalDate from, LocalDate to) {
        LocalDate rangeFrom = from == null ? LocalDate.now().withDayOfYear(1) : from;
        LocalDate rangeTo = to == null ? LocalDate.now().withDayOfYear(1).plusYears(1).minusDays(1) : to;
        List<TrialBalanceRawVO> rawRows = queryMapper.selectTrialBalanceRows(BookContext.getBookId(), rangeFrom, rangeTo);

        BigDecimal totalOpeningDebit = BigDecimal.ZERO;
        BigDecimal totalOpeningCredit = BigDecimal.ZERO;
        BigDecimal totalPeriodDebit = BigDecimal.ZERO;
        BigDecimal totalPeriodCredit = BigDecimal.ZERO;
        BigDecimal totalClosingDebit = BigDecimal.ZERO;
        BigDecimal totalClosingCredit = BigDecimal.ZERO;

        List<TrialBalanceRowVO> rows = rawRows.stream().map(raw -> {
            // 期初/期末余额按借贷净额方向列示（净借方落借栏，净贷方落贷栏）
            BigDecimal openingDebitSum = nz(raw.openingDebitSum());
            BigDecimal openingCreditSum = nz(raw.openingCreditSum());
            BigDecimal periodDebitSum = nz(raw.periodDebitSum());
            BigDecimal periodCreditSum = nz(raw.periodCreditSum());
            BigDecimal openingNet = openingDebitSum.subtract(openingCreditSum);
            BigDecimal closingNet = openingNet.add(periodDebitSum).subtract(periodCreditSum);
            return new TrialBalanceRowVO(raw.subjectId(), raw.code(), raw.name(),
                    raw.category(), raw.direction(),
                    positive(openingNet), negative(openingNet),
                    periodDebitSum, periodCreditSum,
                    positive(closingNet), negative(closingNet));
        }).toList();

        for (TrialBalanceRowVO row : rows) {
            totalOpeningDebit = totalOpeningDebit.add(row.openingDebit());
            totalOpeningCredit = totalOpeningCredit.add(row.openingCredit());
            totalPeriodDebit = totalPeriodDebit.add(row.periodDebit());
            totalPeriodCredit = totalPeriodCredit.add(row.periodCredit());
            totalClosingDebit = totalClosingDebit.add(row.closingDebit());
            totalClosingCredit = totalClosingCredit.add(row.closingCredit());
        }
        // 三组借贷合计必须精确相等（compareTo）才认定试算平衡
        boolean balanced = totalOpeningDebit.compareTo(totalOpeningCredit) == 0
                && totalPeriodDebit.compareTo(totalPeriodCredit) == 0
                && totalClosingDebit.compareTo(totalClosingCredit) == 0;
        return new TrialBalanceVO(rangeFrom, rangeTo, balanced,
                totalOpeningDebit, totalOpeningCredit, totalPeriodDebit, totalPeriodCredit,
                totalClosingDebit, totalClosingCredit, rows);
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
     * 凭证分录全套校验（新增/修改共用）。
     *
     * @return 借贷合计
     * @throws BusinessException 分录不足两行 / 科目非法 / 借贷单边规则失败 / 借贷不平衡
     */
    private VoucherTotals validateEntries(List<VoucherForm.Entry> entries) {
        if (entries == null || entries.size() < 2) {
            throw BusinessException.ofKey("erp.fin.voucher-min-two-lines");
        }
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        int lineNo = 1;
        for (VoucherForm.Entry entry : entries) {
            FinSubject subject = subjectMapper.selectById(entry.subjectId());
            if (subject == null) {
                throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.line-subject-not-exists", lineNo, entry.subjectId());
            }
            // 分录科目必须属于当前账套（科目树按账套隔离）
            BookContext.checkBookOwnership(subject.getBookId(),
                    "第%s行科目[%s %s]".formatted(lineNo, subject.getCode(), subject.getName()));
            if (subject.getStatus() == null || subject.getStatus() != 1) {
                throw BusinessException.ofKey("erp.fin.line-subject-disabled", lineNo, subject.getCode(), subject.getName());
            }
            Long children = subjectMapper.selectCount(Wrappers.lambdaQuery(FinSubject.class)
                    .eq(FinSubject::getBookId, BookContext.getBookId())
                    .eq(FinSubject::getParentId, subject.getId()));
            if (children > 0) {
                throw BusinessException.ofKey("erp.fin.line-subject-not-leaf", lineNo, subject.getCode(), subject.getName());
            }
            BigDecimal debit = nz(entry.debitAmount());
            BigDecimal credit = nz(entry.creditAmount());
            if (debit.signum() == 0 && credit.signum() == 0) {
                throw BusinessException.ofKey("erp.fin.line-debit-credit-zero", lineNo);
            }
            if (debit.signum() > 0 && credit.signum() > 0) {
                throw BusinessException.ofKey("erp.fin.line-debit-credit-positive", lineNo);
            }
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
            lineNo++;
        }
        // DECIMAL 精确比较必须用 compareTo（equals 会把 1.0 与 1.00 判为不等）
        if (totalDebit.compareTo(totalCredit) != 0) {
            // warn：借贷不平是会计红线，差值留痕便于快速定位金额错在哪几行
            log.warn("[凭证][校验] 拒绝 原因=借贷不平衡 借方合计={} 贷方合计={} 差额={} 分录数={}",
                    totalDebit.toPlainString(), totalCredit.toPlainString(),
                    totalDebit.subtract(totalCredit).toPlainString(), entries.size());
            throw BusinessException.ofKey("erp.fin.unbalanced", totalDebit.toPlainString(), totalCredit.toPlainString());
        }
        return new VoucherTotals(totalDebit, totalCredit);
    }

    /** 按表单插入分录（行号 1..n 连续，归属当前账套） */
    private void insertEntries(Long voucherId, List<VoucherForm.Entry> entries) {
        int lineNo = 1;
        Long bookId = BookContext.getBookId();
        for (VoucherForm.Entry form : entries) {
            FinVoucherEntry entry = new FinVoucherEntry();
            entry.setBookId(bookId);
            entry.setVoucherId(voucherId);
            entry.setLineNo(lineNo++);
            entry.setSummary(form.summary());
            entry.setSubjectId(form.subjectId());
            entry.setDebitAmount(nz(form.debitAmount()));
            entry.setCreditAmount(nz(form.creditAmount()));
            entryMapper.insert(entry);
        }
    }

    /** 凭证必须存在且属于当前账套（跨账套 ID 视同不存在，防横向越权） */
    private FinVoucher requireVoucher(Long id) {
        FinVoucher voucher = voucherMapper.selectById(id);
        if (voucher == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.voucher-not-exists", id);
        }
        BookContext.checkBookOwnership(voucher.getBookId(), "凭证[%s]".formatted(voucher.getVoucherNo()));
        return voucher;
    }

    /** 凭证必须存在且处于草稿状态（过账后禁改禁删） */
    private FinVoucher requireEditableVoucher(Long id) {
        FinVoucher voucher = requireVoucher(id);
        if (STATUS_POSTED.equals(voucher.getStatus())) {
            throw BusinessException.ofKey("erp.fin.voucher-posted-no-modify");
        }
        return voucher;
    }

    /** 科目 ID 集合 → id -> 实体 */
    private Map<Long, FinSubject> subjectMap(List<Long> subjectIds) {
        return subjectIds.isEmpty() ? Map.of()
                : subjectMapper.selectBatchIds(subjectIds).stream()
                        .collect(Collectors.toMap(FinSubject::getId, Function.identity()));
    }

    /** 净借方余额（&gt;0 取值，否则 0） */
    private BigDecimal positive(BigDecimal net) {
        return net.signum() > 0 ? net : BigDecimal.ZERO;
    }

    /** 净贷方余额（&lt;0 取绝对值，否则 0） */
    private BigDecimal negative(BigDecimal net) {
        return net.signum() < 0 ? net.negate() : BigDecimal.ZERO;
    }

    /** null 转 0 */
    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 聚合结果判空（无记录时 MyBatis 返回 null 或列全 null） */
    private SubjectAggregateRawVO nz(SubjectAggregateRawVO raw) {
        if (raw == null) {
            return new SubjectAggregateRawVO(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return new SubjectAggregateRawVO(nz(raw.openingDebitSum()), nz(raw.openingCreditSum()),
                nz(raw.periodDebitSum()), nz(raw.periodCreditSum()));
    }

    private static LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    /** 借贷合计对 */
    private record VoucherTotals(BigDecimal debit, BigDecimal credit) {
    }
}
