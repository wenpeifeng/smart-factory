package com.erp.mrp.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.mrp.dto.form.MpsForm;
import com.erp.mrp.dto.vo.MpsVO;
import com.erp.mrp.service.MpsService;
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
 * 主生产计划 MPS 接口。
 */
@Tag(name = "生产制造-主产计划")
@RestController
@RequestMapping("/mrp/mps")
@RequiredArgsConstructor
public class MrpMpsController {

    private final MpsService mpsService;

    /** 分页查询 MPS */
    @Operation(summary = "分页查询MPS")
    @RequirePerm("mrp:mps:list")
    @GetMapping
    public Result<PageResult<MpsVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(mpsService.page(pageNum, pageSize, keyword, status));
    }

    /** 新建 MPS 行 */
    @Operation(summary = "新建MPS")
    @RequirePerm("mrp:mps:add")
    @OperLog(module = "生产制造", operation = "新增MPS计划")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody MpsForm form) {
        return Result.ok(mpsService.create(form));
    }

    /** 修改 MPS 行 */
    @Operation(summary = "修改MPS")
    @RequirePerm("mrp:mps:edit")
    @OperLog(module = "生产制造", operation = "修改MPS计划")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody MpsForm form) {
        mpsService.update(id, form);
        return Result.ok();
    }

    /** 删除 MPS 行 */
    @Operation(summary = "删除MPS")
    @RequirePerm("mrp:mps:delete")
    @OperLog(module = "生产制造", operation = "删除MPS计划")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mpsService.delete(id);
        return Result.ok();
    }
}
