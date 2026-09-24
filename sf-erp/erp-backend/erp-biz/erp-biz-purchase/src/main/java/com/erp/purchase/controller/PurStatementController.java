package com.erp.purchase.controller;

import com.erp.common.result.Result;
import com.erp.purchase.dto.vo.SupplierStatementVO;
import com.erp.purchase.service.PurStatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.framework.security.RequirePerm;

/**
 * 供应商对账单接口（只读统计）。
 */
@Tag(name = "供应商对账单")
@RestController
@RequestMapping("/purchase/statements")
@RequiredArgsConstructor
public class PurStatementController {

    private final PurStatementService purStatementService;

    /** 按供应商+月份汇总采购入库/退货/净额（仅统计已审核单据） */
    @Operation(summary = "供应商月度对账汇总")
    @RequirePerm("purchase:statement:list")
    @GetMapping
    public Result<SupplierStatementVO> statement(
            @Parameter(description = "供应商 ID") @RequestParam Long supplierId,
            @Parameter(description = "对账月份 yyyy-MM") @RequestParam String month) {
        return Result.ok(purStatementService.statement(supplierId, month));
    }
}
