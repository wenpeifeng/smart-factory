package com.erp.finance.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.finance.dto.form.ReceiptForm;
import com.erp.finance.dto.request.ReceiptQuery;
import com.erp.finance.dto.vo.ReceiptDetailVO;
import com.erp.finance.dto.vo.ReceiptVO;
import com.erp.finance.service.FinReceiptService;
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
 * 收款单接口：收款核销应收（保存后回写应收状态、账户余额并记流水）。
 */
@Tag(name = "财务管理-收款单")
@RestController
@RequestMapping("/finance/receipts")
@RequiredArgsConstructor
public class FinReceiptController {

    private final FinReceiptService receiptService;

    @Operation(summary = "分页查询收款单")
    @RequirePerm("finance:receipt:list")
    @GetMapping
    public Result<PageResult<ReceiptVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String keyword) {
        return Result.ok(receiptService.page(
                new ReceiptQuery(customerId, month, keyword), pageNum, pageSize));
    }

    @Operation(summary = "收款单明细（含核销明细行）")
    @RequirePerm("finance:receipt:list")
    @GetMapping("/{id}")
    public Result<ReceiptDetailVO> detail(@PathVariable Long id) {
        return Result.ok(receiptService.detail(id));
    }

    @Operation(summary = "保存收款单并核销应收（事务）")
    @RequirePerm("finance:receipt:add")
    @OperLog(module = "财务管理", operation = "新增收款单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ReceiptForm form) {
        return Result.ok(receiptService.create(form));
    }
}
