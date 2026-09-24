package com.erp.inventory.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.inventory.dto.form.SerialOutForm;
import com.erp.inventory.dto.form.SerialRegisterForm;
import com.erp.inventory.dto.form.SerialScrapForm;
import com.erp.inventory.dto.request.SerialQuery;
import com.erp.inventory.dto.vo.InvSerialVO;
import com.erp.inventory.service.InvSerialService;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 序列号接口（V422）：入库登记 / 出库核销 / 报废 / 正反查追溯。
 */
@Tag(name = "库存管理-序列号")
@RestController
@RequestMapping("/inventory/serials")
@RequiredArgsConstructor
public class InvSerialController {

    private final InvSerialService serialService;

    /** 序列号台账分页（正查=serialNo 精确/前缀；反查=materialId） */
    @Operation(summary = "序列号分页(正反查)")
    @RequirePerm("inventory:wms:serial:list")
    @GetMapping
    public Result<PageResult<InvSerialVO>> page(SerialQuery query) {
        return Result.ok(serialService.page(query));
    }

    /** 入库登记（批量导入/逐条共用：serialNos 一次最多 500 条） */
    @Operation(summary = "序列号入库登记")
    @RequirePerm("inventory:wms:serial:edit")
    @OperLog(module = "序列号管理", operation = "入库登记")
    @Idempotent
    @PostMapping
    public Result<Map<String, Integer>> register(@Valid @RequestBody SerialRegisterForm form) {
        return Result.ok(Map.of("registered", serialService.register(form)));
    }

    /** 出库核验（IN_STOCK → OUT，写 out_no；任一不满足整体回滚） */
    @Operation(summary = "序列号出库核销")
    @RequirePerm("inventory:wms:serial:audit")
    @OperLog(module = "序列号管理", operation = "出库核销")
    @Idempotent
    @PostMapping("/write-off")
    public Result<Map<String, Integer>> writeOff(@Valid @RequestBody SerialOutForm form) {
        return Result.ok(Map.of("writtenOff", serialService.writeOff(form)));
    }

    /** 报废（IN_STOCK → SCRAP） */
    @Operation(summary = "序列号报废")
    @RequirePerm("inventory:wms:serial:audit")
    @OperLog(module = "序列号管理", operation = "报废")
    @Idempotent
    @PostMapping("/scrap")
    public Result<Map<String, Integer>> scrap(@Valid @RequestBody SerialScrapForm form) {
        return Result.ok(Map.of("scrapped", serialService.scrap(form.getSerialNos(), form.getRemark())));
    }
}
