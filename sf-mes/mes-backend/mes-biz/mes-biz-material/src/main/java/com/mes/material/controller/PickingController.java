package com.mes.material.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.material.dto.form.PickingForm;
import com.mes.material.dto.vo.PickingVO;
import com.mes.material.service.PickingService;
import com.mes.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 领料单接口。
 */
@Tag(name = "物料-领料单")
@RestController
@RequestMapping("/material/pickings")
@RequiredArgsConstructor
public class PickingController {

    private final PickingService pickingService;

    /**
     * 创建领料单。
     */
    @Operation(summary = "创建领料单")
    @RequirePerm("material:picking:add")
    @OperLog(module = "领料单", operation = "创建领料单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PickingForm form) {
        return Result.ok(pickingService.create(form));
    }

    /**
     * 删除领料单（仅草稿）。
     */
    @Operation(summary = "删除领料单")
    @RequirePerm("material:picking:delete")
    @OperLog(module = "领料单", operation = "删除领料单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        pickingService.delete(id);
        return Result.ok();
    }

    /**
     * 审核领料单。
     */
    @Operation(summary = "审核领料单")
    @RequirePerm("material:picking:audit")
    @OperLog(module = "领料单", operation = "审核领料单")
    @Idempotent
    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id) {
        pickingService.audit(id);
        return Result.ok();
    }

    /**
     * 分页查询领料单。
     */
    @Operation(summary = "分页查询领料单")
    @RequirePerm("material:picking:list")
    @GetMapping
    public Result<PageResult<PickingVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "10") long pageSize,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status) {
        return Result.ok(pickingService.page(pageNum, pageSize, keyword, status));
    }

    /**
     * 领料单详情。
     */
    @Operation(summary = "领料单详情")
    @RequirePerm("material:picking:list")
    @GetMapping("/{id}")
    public Result<PickingVO> detail(@PathVariable Long id) {
        return Result.ok(pickingService.detail(id));
    }
}
