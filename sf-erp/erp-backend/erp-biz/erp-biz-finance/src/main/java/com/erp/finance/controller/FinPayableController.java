package com.erp.finance.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.finance.dto.form.PayableForm;
import com.erp.finance.dto.request.PayableQuery;
import com.erp.finance.dto.vo.PayableVO;
import com.erp.finance.service.FinPayableService;
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
 * 应付管理接口：分页查询（供应商/状态/月份）、明细、手工补录。
 */
@Tag(name = "财务管理-应付管理")
@RestController
@RequestMapping("/finance/payables")
@RequiredArgsConstructor
public class FinPayableController {

    private final FinPayableService payableService;

    @Operation(summary = "分页查询应付单")
    @RequirePerm("finance:payable:list")
    @GetMapping
    public Result<PageResult<PayableVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean unsettledOnly) {
        return Result.ok(payableService.page(
                new PayableQuery(supplierId, status, month, keyword, unsettledOnly), pageNum, pageSize));
    }

    @Operation(summary = "应付单明细")
    @RequirePerm("finance:payable:list")
    @GetMapping("/{id}")
    public Result<PayableVO> detail(@PathVariable Long id) {
        return Result.ok(payableService.detail(id));
    }

    @Operation(summary = "手工补录应付单（正常由采购入库自动生成）")
    @RequirePerm("finance:payable:add")
    @OperLog(module = "财务管理", operation = "新增应付单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PayableForm form) {
        return Result.ok(payableService.create(form));
    }
}
