package com.erp.finance.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.finance.dto.form.PaymentForm;
import com.erp.finance.dto.request.PaymentQuery;
import com.erp.finance.dto.vo.PaymentDetailVO;
import com.erp.finance.dto.vo.PaymentVO;
import com.erp.finance.service.FinPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 付款单接口：付款核销应付（保存后回写应付状态、扣减账户余额并记流水）。
 * <p>
 * 预算控制接线：表单可带 deptId/subjectId（均可空）——均定位到时创建付款单先做
 * 预算占用校验（BLOCK 超预算整体拒绝、WARN 追加备注放行），核销成功后同事务登记
 * 预算执行数；为空则跳过预算校验（兼容老数据）。
 */
@Tag(name = "财务管理-付款单")
@RestController
@RequestMapping("/finance/payments")
@RequiredArgsConstructor
public class FinPaymentController {

    private final FinPaymentService paymentService;

    @Operation(summary = "分页查询付款单")
    @RequirePerm("finance:payment:list")
    @GetMapping
    public Result<PageResult<PaymentVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String keyword) {
        return Result.ok(paymentService.page(
                new PaymentQuery(supplierId, month, keyword), pageNum, pageSize));
    }

    @Operation(summary = "付款单明细（含核销明细行）")
    @RequirePerm("finance:payment:list")
    @GetMapping("/{id}")
    public Result<PaymentDetailVO> detail(@PathVariable Long id) {
        return Result.ok(paymentService.detail(id));
    }

    @Operation(summary = "保存付款单并核销应付（事务）")
    @RequirePerm("finance:payment:add")
    @OperLog(module = "财务管理", operation = "新增付款单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PaymentForm form) {
        return Result.ok(paymentService.create(form));
    }
}
