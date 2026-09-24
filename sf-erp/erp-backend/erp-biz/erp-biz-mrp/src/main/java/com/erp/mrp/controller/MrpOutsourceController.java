package com.erp.mrp.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.mrp.dto.form.OutsourceForm;
import com.erp.mrp.dto.form.OutsourceReceiveForm;
import com.erp.mrp.dto.vo.OutsourceVO;
import com.erp.mrp.service.MrpOutsourceService;
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
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 委外加工接口（简化）：草稿 → 发料扣库存 → 完工入库+加工费应付。
 */
@Tag(name = "生产制造-委外加工")
@RestController
@RequestMapping("/mrp/outsources")
@RequiredArgsConstructor
public class MrpOutsourceController {

    private final MrpOutsourceService outsourceService;

    /** 分页查询委外单 */
    @Operation(summary = "分页查询委外单")
    @RequirePerm("mrp:outsource:list")
    @GetMapping
    public Result<PageResult<OutsourceVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(outsourceService.page(pageNum, pageSize, keyword, status));
    }

    /** 委外单详情 */
    @Operation(summary = "委外单详情")
    @RequirePerm("mrp:outsource:list")
    @GetMapping("/{id}")
    public Result<OutsourceVO> detail(@PathVariable Long id) {
        return Result.ok(outsourceService.detail(id));
    }

    /** 新建草稿委外单（按BOM带出发料明细） */
    @Operation(summary = "新建委外单")
    @RequirePerm("mrp:outsource:add")
    @OperLog(module = "生产制造", operation = "新增委外单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody OutsourceForm form) {
        return Result.ok(outsourceService.create(form));
    }

    /** 修改草稿委外单 */
    @Operation(summary = "修改委外单")
    @RequirePerm("mrp:outsource:edit")
    @OperLog(module = "生产制造", operation = "修改委外单")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody OutsourceForm form) {
        outsourceService.update(id, form);
        return Result.ok();
    }

    /** 删除草稿委外单 */
    @Operation(summary = "删除委外单")
    @RequirePerm("mrp:outsource:delete")
    @OperLog(module = "生产制造", operation = "删除委外单")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        outsourceService.delete(id);
        return Result.ok();
    }

    /** 发料（扣发料仓库存） */
    @Operation(summary = "委外发料")
    @RequirePerm("mrp:outsource:audit")
    @OperLog(module = "生产制造", operation = "委外发料")
    @Idempotent
    @PostMapping("/{id}/issue")
    public Result<Void> issueMaterial(@PathVariable Long id) {
        outsourceService.issueMaterial(id);
        return Result.ok();
    }

    /** 完工入库（成品入库+加工费应付） */
    @Operation(summary = "委外完工入库")
    @RequirePerm("mrp:outsource:audit")
    @OperLog(module = "生产制造", operation = "委外收货")
    @Idempotent
    @PostMapping("/{id}/receive")
    public Result<Void> receive(@PathVariable Long id, @Valid @RequestBody OutsourceReceiveForm form) {
        outsourceService.receive(id, form);
        return Result.ok();
    }
}
