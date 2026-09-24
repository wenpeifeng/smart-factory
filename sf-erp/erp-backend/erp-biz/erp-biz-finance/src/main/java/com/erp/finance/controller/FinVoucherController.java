package com.erp.finance.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.finance.dto.form.VoucherForm;
import com.erp.finance.dto.request.VoucherQuery;
import com.erp.finance.dto.vo.SubjectSummaryVO;
import com.erp.finance.dto.vo.VoucherDetailVO;
import com.erp.finance.dto.vo.VoucherVO;
import com.erp.finance.service.FinVoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 总账凭证接口：手工凭证录入（借贷平衡校验）、审核（过账后禁改禁删）、科目汇总表。
 */
@Tag(name = "财务管理-总账凭证")
@RestController
@RequestMapping("/finance/vouchers")
@RequiredArgsConstructor
public class FinVoucherController {

    private final FinVoucherService voucherService;

    @Operation(summary = "分页查询凭证")
    @RequirePerm("finance:voucher:list")
    @GetMapping
    public Result<PageResult<VoucherVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(voucherService.page(
                new VoucherQuery(keyword, status,
                        from == null ? null : from.toString(), to == null ? null : to.toString()),
                pageNum, pageSize));
    }

    @Operation(summary = "凭证明细（含分录行）")
    @RequirePerm("finance:voucher:list")
    @GetMapping("/{id}")
    public Result<VoucherDetailVO> detail(@PathVariable Long id) {
        return Result.ok(voucherService.detail(id));
    }

    @Operation(summary = "新增凭证（借贷不平衡将被拒绝）")
    @RequirePerm("finance:voucher:add")
    @OperLog(module = "财务管理", operation = "新增凭证")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody VoucherForm form) {
        return Result.ok(voucherService.create(form));
    }

    @Operation(summary = "修改凭证（仅草稿可改）")
    @RequirePerm("finance:voucher:edit")
    @OperLog(module = "财务管理", operation = "修改凭证")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody VoucherForm form) {
        voucherService.update(id, form);
        return Result.ok();
    }

    @Operation(summary = "删除凭证（仅草稿可删）")
    @RequirePerm("finance:voucher:delete")
    @OperLog(module = "财务管理", operation = "删除凭证")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        voucherService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "审核凭证（过账，之后禁改禁删）")
    @RequirePerm("finance:voucher:audit")
    @OperLog(module = "财务管理", operation = "审核凭证")
    @Idempotent
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id) {
        voucherService.audit(id);
        return Result.ok();
    }

    @Operation(summary = "科目汇总表（借方合计/贷方合计/期末余额，仅已过账凭证）")
    @RequirePerm("finance:voucher:list")
    @GetMapping("/summary")
    public Result<SubjectSummaryVO> summary(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(voucherService.summary(subjectId, from, to));
    }
}
