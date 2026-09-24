package com.erp.it;

import com.erp.common.exception.BusinessException;
import com.erp.finance.dto.form.VoucherForm;
import com.erp.finance.dto.vo.TrialBalanceVO;
import com.erp.finance.service.FinVoucherService;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 链路三：总账凭证（FinVoucherService）借贷平衡不变量。
 * <ul>
 *   <li><b>借贷平衡</b>：BigDecimal.compareTo 精确比较——100 vs 90 拒，
 *       100.00 vs 100.01（0.01 尾差）也必须拒（equals 会误判，compareTo 是红线）；</li>
 *   <li><b>单行单边</b>：同一分录借/贷互斥，同借同贷拒绝；</li>
 *   <li><b>过账保护</b>：DRAFT→POSTED 单向，过账后禁改禁删；</li>
 *   <li><b>试算平衡</b>：只统计 POSTED 凭证，三组借贷合计 compareTo 相等 → balanced=true。</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("链路3-凭证借贷平衡与过账保护")
class VoucherBalanceIT extends ItTestSupport {

    @Autowired
    private FinVoucherService voucherService;

    /** 种子科目：1001 库存现金 / 1002 银行存款（末级、启用，book 1001） */
    private Long cashSubjectId;
    private Long bankSubjectId;

    @BeforeAll
    void setUpSubjects() {
        cashSubjectId = subjectIdByCode("1001");
        bankSubjectId = subjectIdByCode("1002");
        assertThat(cashSubjectId).as("V501 种子科目 1001 应存在").isNotNull();
        assertThat(bankSubjectId).as("V501 种子科目 1002 应存在").isNotNull();
    }

    @Test
    @Order(1)
    @DisplayName("借贷不平凭证被拒：100 vs 90，以及 0.01 级尾差")
    void unbalancedVoucher_isRejected() {
        // 大额不平：借 100 / 贷 90
        Throwable tex = catchThrowable(() -> voucherService.create(form(
                entry("IT测试-借现金", cashSubjectId, "100", null),
                entry("IT测试-贷银行", bankSubjectId, null, "90"))));
        assertBizMsg(tex, "借贷不平衡");

        // 0.01 级尾差也必须拒（compareTo 精确比较，非 equals）
        Throwable tex2 = catchThrowable(() -> voucherService.create(form(
                entry("IT测试-借现金", cashSubjectId, "100.00", null),
                entry("IT测试-贷银行", bankSubjectId, null, "100.01"))));
        assertBizMsg(tex2, "借贷不平衡");

        // 分录不足两行
        Throwable tex3 = catchThrowable(() -> voucherService.create(form(
                entry("IT测试-单行", cashSubjectId, "100", null))));
        assertBizMsg(tex3, "至少需要两条分录");

        // 校验失败不落库
        Integer drafts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM fin_voucher WHERE remark='IT-VCH-REJECT-TEST'",
                Integer.class);
        assertThat(drafts).as("不平凭证零落库").isZero();
    }

    @Test
    @Order(2)
    @DisplayName("同一分录借方贷方同填被拒")
    void entryWithBothDebitAndCredit_isRejected() {
        Throwable tex4 = catchThrowable(() -> voucherService.create(form(
                entry("IT测试-同借同贷", cashSubjectId, "100", "100"),
                entry("IT测试-贷银行", bankSubjectId, null, "100"))));
        assertBizMsg(tex4, "不能同时大于0");
    }

    @Test
    @Order(3)
    @DisplayName("平衡凭证保存→过账→修改/删除均被拒（DRAFT→POSTED 单向）")
    void postedVoucher_cannotBeModifiedOrDeleted() {
        Long voucherId = voucherService.create(form(
                entry("IT测试-借现金", cashSubjectId, "200", null),
                entry("IT测试-贷银行", bankSubjectId, null, "200")));

        voucherService.audit(voucherId);

        String status = jdbc.queryForObject(
                "SELECT status FROM fin_voucher WHERE id=?", String.class, voucherId);
        assertThat(status).as("过账后状态 POSTED").isEqualTo("POSTED");

        // 过账后修改被拒
        VoucherForm modify = form(
                entry("IT测试-改借", cashSubjectId, "999", null),
                entry("IT测试-改贷", bankSubjectId, null, "999"));
        Throwable tex5 = catchThrowable(() -> voucherService.update(voucherId, modify));
        assertBizMsg(tex5, "已过账，禁止修改或删除");

        // 过账后删除被拒
        Throwable tex6 = catchThrowable(() -> voucherService.delete(voucherId));
        assertBizMsg(tex6, "已过账，禁止修改或删除");

        // 重复过账被拒
        Throwable tex7 = catchThrowable(() -> voucherService.audit(voucherId));
        assertBizMsg(tex7, "已过账");

        // 数据原封未动
        BigDecimal debit = jdbc.queryForObject(
                "SELECT total_debit FROM fin_voucher WHERE id=?", BigDecimal.class, voucherId);
        assertThat(debit).as("过账凭证金额未被篡改").isEqualByComparingTo("200");
    }

    @Test
    @Order(4)
    @DisplayName("试算平衡：POSTED 凭证计入且三组借贷相等，DRAFT 不计入")
    void trialBalance_isBalancedAndOnlyCountsPosted() {
        // 先建一张 DRAFT（不应进入试算平衡口径）
        TrialBalanceVO before = voucherService.trialBalance(null, null);
        BigDecimal beforeDebit = before.totalPeriodDebit();

        Long draftId = voucherService.create(form(
                entry("IT测试-草稿借", cashSubjectId, "999", null),
                entry("IT测试-草稿贷", bankSubjectId, null, "999")));
        assertThat(jdbc.queryForObject(
                "SELECT status FROM fin_voucher WHERE id=?", String.class, draftId))
                .isEqualTo("DRAFT");

        TrialBalanceVO after = voucherService.trialBalance(null, null);
        assertThat(after.balanced()).as("试算平衡 balanced=true").isTrue();
        assertThat(after.totalPeriodDebit())
                .as("DRAFT 凭证不计入试算平衡")
                .isEqualByComparingTo(beforeDebit);
        assertThat(after.totalPeriodDebit())
                .as("本期借方合计=贷方合计（含 Order3 过账的 200）")
                .isEqualByComparingTo(after.totalPeriodCredit());
        assertThat(after.totalPeriodDebit())
                .as("试算平衡至少包含已过账的 200")
                .isGreaterThanOrEqualTo(new BigDecimal("200"));
        assertThat(after.totalClosingDebit()).isEqualByComparingTo(after.totalClosingCredit());
        assertThat(after.totalOpeningDebit()).isEqualByComparingTo(after.totalOpeningCredit());
    }

    // ------------------------------------------------------------------
    // 造数 helper
    // ------------------------------------------------------------------

    private Long subjectIdByCode(String code) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM fin_subject WHERE code=? AND book_id=1001 AND deleted=0 "
                        + "AND status=1 ORDER BY id LIMIT 1", Long.class, code);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private VoucherForm form(VoucherForm.Entry... entries) {
        List<VoucherForm.Entry> list = new ArrayList<>(List.of(entries));
        return new VoucherForm(LocalDate.now(), "记", 0, "IT-VCH-REJECT-TEST", list);
    }

    private VoucherForm.Entry entry(String summary, Long subjectId, String debit, String credit) {
        return new VoucherForm.Entry(summary, subjectId,
                debit == null ? null : new BigDecimal(debit),
                credit == null ? null : new BigDecimal(credit));
    }
}
