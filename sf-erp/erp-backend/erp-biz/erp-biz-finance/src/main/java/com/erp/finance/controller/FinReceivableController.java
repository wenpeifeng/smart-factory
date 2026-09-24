package com.erp.finance.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.finance.dto.form.ReceivableForm;
import com.erp.finance.dto.request.ReceivableQuery;
import com.erp.finance.dto.vo.ReceivableVO;
import com.erp.finance.service.FinReceivableService;
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
 * 应收管理接口：分页查询（客户/状态/月份）、明细、手工补录。
 */
@Tag(name = "财务管理-应收管理")
@RestController
@RequestMapping("/finance/receivables")
@RequiredArgsConstructor
public class FinReceivableController {

    private final FinReceivableService receivableService;

    @Operation(summary = "分页查询应收单")
    @RequirePerm("finance:receivable:list")
    @GetMapping
    public Result<PageResult<ReceivableVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean unsettledOnly) {
        return Result.ok(receivableService.page(
                new ReceivableQuery(customerId, status, month, keyword, unsettledOnly), pageNum, pageSize));
    }

    @Operation(summary = "应收单明细")
    @RequirePerm("finance:receivable:list")
    @GetMapping("/{id}")
    public Result<ReceivableVO> detail(@PathVariable Long id) {
        return Result.ok(receivableService.detail(id));
    }

    @Operation(summary = "手工补录应收单（正常由销售出库自动生成）")
    @RequirePerm("finance:receivable:add")
    @OperLog(module = "财务管理", operation = "新增应收单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ReceivableForm form) {
        return Result.ok(receivableService.create(form));
    }
}
