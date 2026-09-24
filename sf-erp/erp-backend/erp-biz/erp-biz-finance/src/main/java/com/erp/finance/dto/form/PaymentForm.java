package com.erp.finance.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 付款单（核销应付）提交表单，与收款单对称。
 * <p>
 * 约束：付款金额必须等于明细核销额之和；明细必须属于同一供应商；
 * 每笔核销额不得超过该应付的未核销余额；付款后账户余额不得为负。
 * <p>
 * 预算控制（finance ↔ budget 接线）：deptId/subjectId 为可选归属字段——
 * 均非空时创建付款单先做预算占用校验（BLOCK 超预算整体拒绝 / WARN 追加备注提示），
 * 核销成功后同事务登记预算执行数；为空则跳过预算校验（兼容老数据）。
 *
 * @param supplierId 供应商 ID
 * @param accountId  付款资金账户 ID
 * @param bizDate    付款日期
 * @param amount     付款金额
 * @param remark     备注
 * @param deptId     费用归属部门（可选，预算控制用；为空时尝试按供应商最近采购/应付归属部门推导）
 * @param subjectId  预算科目（可选，fin_subject.id；为空则跳过预算校验）
 * @param items      核销明细（至少一条）
 */
public record PaymentForm(
        @NotNull(message = "供应商不能为空") Long supplierId,
        @NotNull(message = "付款账户不能为空") Long accountId,
        @NotNull(message = "付款日期不能为空") LocalDate bizDate,
        @NotNull(message = "付款金额不能为空")
        @DecimalMin(value = "0.01", message = "付款金额必须大于0") BigDecimal amount,
        String remark,
        Long deptId,
        Long subjectId,
        @NotEmpty(message = "核销明细不能为空")
        @Valid List<Item> items) {

    /**
     * 付款核销明细行。
     *
     * @param payableId   应付单 ID
     * @param verifyAmount 本次核销额（&gt; 0，不得超过未核销余额）
     */
    public record Item(
            @NotNull(message = "应付单不能为空") Long payableId,
            @NotNull(message = "核销金额不能为空")
            @DecimalMin(value = "0.01", message = "核销金额必须大于0") BigDecimal verifyAmount) {
    }
}
