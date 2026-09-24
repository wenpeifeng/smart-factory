package com.erp.inventory.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.inventory.dto.form.OtherInForm;
import com.erp.inventory.dto.request.OtherDocQuery;
import com.erp.inventory.dto.vo.OtherDocDetailVO;
import com.erp.inventory.dto.vo.OtherDocVO;
import com.erp.inventory.service.InvOtherInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 其他入库单接口（无来源手工入库：草稿 → 审核过账）。
 */
@Tag(name = "库存管理-其他入库")
@RestController
@RequestMapping("/inventory/other-ins")
@RequiredArgsConstructor
public class OtherInController {

    private final InvOtherInService otherInService;

    /** 分页查询其他入库单 */
    @Operation(summary = "其他入库单分页查询")
    @RequirePerm("inventory:move:list")
    @GetMapping
    public Result<PageResult<OtherDocVO>> page(OtherDocQuery query) {
        return Result.ok(otherInService.page(query));
    }

    /** 单据详情（头 + 明细行） */
    @Operation(summary = "其他入库单详情")
    @RequirePerm("inventory:move:list")
    @GetMapping("/{id}")
    public Result<OtherDocDetailVO> detail(@PathVariable Long id) {
        return Result.ok(otherInService.detail(id));
    }

    /** 新建其他入库单（草稿，不动库存） */
    @Operation(summary = "新建其他入库单")
    @RequirePerm("inventory:move:add")
    @OperLog(module = "库存管理", operation = "新增其他入库单")
    @Idempotent
    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody OtherInForm form) {
        Long id = otherInService.create(form);
        return Result.ok(Map.of("id", id));
    }

    /** 审核过账（逐行加库存 + 落 OTH_IN 流水） */
    @Operation(summary = "审核其他入库单")
    @RequirePerm("inventory:move:audit")
    @OperLog(module = "库存管理", operation = "审核其他入库单")
    @Idempotent
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id) {
        otherInService.audit(id);
        return Result.ok();
    }

    /** 删除草稿单据 */
    @Operation(summary = "删除其他入库单(仅草稿)")
    @RequirePerm("inventory:move:delete")
    @OperLog(module = "库存管理", operation = "删除其他入库单")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        otherInService.delete(id);
        return Result.ok();
    }
}
