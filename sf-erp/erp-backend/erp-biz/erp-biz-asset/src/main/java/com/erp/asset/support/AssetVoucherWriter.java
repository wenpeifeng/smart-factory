package com.erp.asset.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.asset.entity.FinSubject;
import com.erp.asset.entity.FinVoucher;
import com.erp.asset.entity.FinVoucherEntry;
import com.erp.asset.mapper.AstSubjectMapper;
import com.erp.asset.mapper.AstVoucherEntryMapper;
import com.erp.asset.mapper.AstVoucherMapper;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 自动凭证生成器（固定资产模块专用）。
 * <p>
 * 入账 / 折旧计提 / 处置三类业务凭证统一从这里落库，规则与 erp-finance
 * FinVoucherServiceImpl 对齐（fin_voucher / fin_voucher_entry 为 finance 的表，
 * 本模块按协作约定直写）：分录 ≥2 行、借贷单边、科目启用且末级、
 * 借贷平衡用 compareTo 精确比较。自动凭证生成即 <b>POSTED</b>（业务单据驱动的
 * 凭证不允许在手工凭证界面修改/删除，杜绝账实脱节）。
 */
@Component
@RequiredArgsConstructor
public class AssetVoucherWriter {

    /** 凭证状态：已过账（自动凭证终态） */
    public static final String STATUS_POSTED = "POSTED";

    /** 凭证字：转（三类业务均不涉及现金/银行收付主体，统一用"转"） */
    public static final String WORD_TRANSFER = "转";

    private final AstVoucherMapper voucherMapper;
    private final AstVoucherEntryMapper entryMapper;
    private final AstSubjectMapper subjectMapper;
    private final AssetBizNoGenerator bizNoGenerator;

    /**
     * 单条分录规格（借/贷单边：一侧 &gt;0 另一侧 = 0）。
     *
     * @param subjectId 科目 ID（必须启用且末级）
     * @param summary   摘要
     * @param debit     借方金额（无则 0）
     * @param credit    贷方金额（无则 0）
     */
    public record EntrySpec(Long subjectId, String summary, BigDecimal debit, BigDecimal credit) {
    }

    /**
     * 生成一张已过账（POSTED）的借贷平衡凭证。
     * <p>
     * 必须在外层业务事务内调用（加入当前事务，业务失败整体回滚，不留孤儿凭证）。
     *
     * @param voucherDate 凭证日期（入账=入账日期；折旧=期间末日；处置=当天）
     * @param entries     分录（≥2 行，借贷合计必须相等）
     * @param remark      备注（写入 fin_voucher.remark）
     * @return 凭证 ID
     * @throws BusinessException 分录不足 / 科目非法 / 借贷不平衡
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.MANDATORY)
    public Long createPostedVoucher(LocalDate voucherDate, List<EntrySpec> entries, String remark) {
        if (entries == null || entries.size() < 2) {
            throw BusinessException.ofKey("erp.asset.voucher-min-two-lines");
        }
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (EntrySpec spec : entries) {
            FinSubject subject = requireSubject(spec.subjectId());
            BigDecimal debit = nz(spec.debit());
            BigDecimal credit = nz(spec.credit());
            if (debit.signum() == 0 && credit.signum() == 0) {
                throw BusinessException.ofKey("erp.asset.voucher-line-zero", subject.getCode(), subject.getName());
            }
            if (debit.signum() > 0 && credit.signum() > 0) {
                throw BusinessException.ofKey("erp.asset.voucher-line-both-positive", subject.getCode(), subject.getName());
            }
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }
        // DECIMAL 精确比较必须用 compareTo（equals 会把 1.0 与 1.00 判为不等）
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw BusinessException.ofKey("erp.asset.voucher-unbalanced", totalDebit.toPlainString(), totalCredit.toPlainString());
        }

        FinVoucher voucher = new FinVoucher();
        voucher.setVoucherNo(bizNoGenerator.nextVoucherNo());
        voucher.setVoucherDate(voucherDate);
        voucher.setWord(WORD_TRANSFER);
        voucher.setAttachmentCount(0);
        voucher.setTotalDebit(totalDebit);
        voucher.setTotalCredit(totalCredit);
        voucher.setStatus(STATUS_POSTED);
        voucher.setAuditTime(LocalDateTime.now());
        voucher.setRemark(remark);
        voucherMapper.insert(voucher);

        int lineNo = 1;
        for (EntrySpec spec : entries) {
            FinVoucherEntry entry = new FinVoucherEntry();
            entry.setVoucherId(voucher.getId());
            entry.setLineNo(lineNo++);
            entry.setSummary(spec.summary());
            entry.setSubjectId(spec.subjectId());
            entry.setDebitAmount(nz(spec.debit()));
            entry.setCreditAmount(nz(spec.credit()));
            entryMapper.insert(entry);
        }
        return voucher.getId();
    }

    /**
     * 按编码解析科目（启用 + 末级校验）。
     *
     * @param code 科目编码（如 2202 应付账款 / 1001 库存现金 / 1606 固定资产清理）
     * @return 科目实体
     * @throws BusinessException 科目不存在或已停用/非末级
     */
    public FinSubject requireSubjectByCode(String code) {
        FinSubject subject = subjectMapper.selectOne(
                Wrappers.lambdaQuery(FinSubject.class).eq(FinSubject::getCode, code).last("LIMIT 1"));
        if (subject == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.asset.account-not-exists-maintain", code);
        }
        validateUsable(subject);
        return subject;
    }

    /** 按主键解析科目（启用 + 末级校验） */
    public FinSubject requireSubject(Long id) {
        FinSubject subject = subjectMapper.selectById(id);
        if (subject == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.subject-not-exists", id);
        }
        validateUsable(subject);
        return subject;
    }

    /** 科目必须启用且为末级（与手工凭证规则一致） */
    private void validateUsable(FinSubject subject) {
        if (subject.getStatus() == null || subject.getStatus() != 1) {
            throw BusinessException.ofKey("erp.asset.account-disabled", subject.getCode(), subject.getName());
        }
        Long children = subjectMapper.selectCount(Wrappers.lambdaQuery(FinSubject.class)
                .eq(FinSubject::getParentId, subject.getId()));
        if (children != null && children > 0) {
            throw BusinessException.ofKey("erp.asset.account-not-leaf", subject.getCode(), subject.getName());
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
