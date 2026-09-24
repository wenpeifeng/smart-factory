package com.erp.inventory.controller;

import com.erp.common.result.Result;
import com.erp.inventory.dto.form.TransferForm;
import com.erp.inventory.service.StockTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 仓库调拨接口（即时执行：一出一入，同一事务）。
 */
@Tag(name = "库存管理-仓库调拨")
@RestController
@RequestMapping("/inventory/transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService transferService;

    /** 提交调拨：多行一个事务，返回调拨单号（TR+日期+流水） */
    @Operation(summary = "执行仓库调拨")
    @RequirePerm("inventory:move:add")
    @OperLog(module = "库存管理", operation = "库存调拨")
    @Idempotent
    @PostMapping
    public Result<Map<String, String>> transfer(@Valid @RequestBody TransferForm form) {
        String trNo = transferService.transfer(form);
        return Result.ok(Map.of("transferNo", trNo));
    }
}
