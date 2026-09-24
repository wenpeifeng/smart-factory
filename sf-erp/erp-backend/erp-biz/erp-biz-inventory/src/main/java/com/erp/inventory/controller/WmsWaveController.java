package com.erp.inventory.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.inventory.dto.form.WaveCreateForm;
import com.erp.inventory.dto.request.WmsTaskQuery;
import com.erp.inventory.dto.vo.OutboundDocVO;
import com.erp.inventory.dto.vo.WmsTaskVO;
import com.erp.inventory.dto.vo.WmsWaveVO;
import com.erp.inventory.service.WmsWaveService;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;
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

import java.util.List;
import java.util.Map;

/**
 * WMS 波次接口（V421）：建波 / 波次列表详情 / 拣选路径序 / 完成波次。
 */
@Tag(name = "库存管理-WMS波次")
@RestController
@RequestMapping("/inventory/wms/waves")
@RequiredArgsConstructor
public class WmsWaveController {

    private final WmsWaveService waveService;

    /** 波次分页 */
    @Operation(summary = "波次分页")
    @RequirePerm("inventory:wms:wave:list")
    @GetMapping
    public Result<PageResult<WmsWaveVO>> page(WmsTaskQuery query) {
        return Result.ok(waveService.page(query));
    }

    /** 建波可选出库单清单（已审核且未全部生成拣选任务） */
    @Operation(summary = "建波可选出库单")
    @RequirePerm("inventory:wms:wave:list")
    @GetMapping("/outbound-docs")
    public Result<List<OutboundDocVO>> outboundDocs(@RequestParam(required = false) Long warehouseId) {
        return Result.ok(waveService.outboundDocs(warehouseId));
    }

    /** 建波：选多张已审核出库单生成拣选任务并入波 */
    @Operation(summary = "创建波次")
    @RequirePerm("inventory:wms:wave:edit")
    @OperLog(module = "WMS波次", operation = "创建波次")
    @Idempotent
    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody WaveCreateForm form) {
        return Result.ok(Map.of("id", waveService.create(form)));
    }

    /** 波次详情（头 + 进度统计） */
    @Operation(summary = "波次详情")
    @RequirePerm("inventory:wms:wave:list")
    @GetMapping("/{id}")
    public Result<WmsWaveVO> detail(@PathVariable Long id) {
        return Result.ok(waveService.detail(id));
    }

    /** 拣选路径序：波内任务按库位编码升序（最简拣选路径） */
    @Operation(summary = "波次拣选路径序")
    @RequirePerm("inventory:wms:wave:list")
    @GetMapping("/{id}/pick-path")
    public Result<List<WmsTaskVO>> pickPath(@PathVariable Long id) {
        return Result.ok(waveService.pickPath(id));
    }

    /** 完成波次（波内任务全部完成后） */
    @Operation(summary = "完成波次")
    @RequirePerm("inventory:wms:wave:audit")
    @OperLog(module = "WMS波次", operation = "完成波次")
    @Idempotent
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        waveService.complete(id);
        return Result.ok();
    }
}
