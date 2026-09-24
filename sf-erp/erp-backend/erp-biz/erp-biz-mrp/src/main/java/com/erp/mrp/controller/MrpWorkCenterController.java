package com.erp.mrp.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.mrp.dto.form.WorkCenterForm;
import com.erp.mrp.dto.vo.WorkCenterVO;
import com.erp.mrp.service.MrpWorkCenterService;
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

import java.util.List;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 工作中心接口：CRUD + 选择器列表（编码 WC+流水自动生成）。
 */
@Tag(name = "生产制造-工作中心")
@RestController
@RequestMapping("/mrp/work-centers")
@RequiredArgsConstructor
public class MrpWorkCenterController {

    private final MrpWorkCenterService workCenterService;

    /** 分页查询工作中心 */
    @Operation(summary = "分页查询工作中心")
    @RequirePerm("mrp:capacity:list")
    @GetMapping
    public Result<PageResult<WorkCenterVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(workCenterService.page(pageNum, pageSize, keyword, status));
    }

    /** 全部工作中心（选择器） */
    @Operation(summary = "工作中心选择器数据")
    @RequirePerm("mrp:capacity:list")
    @GetMapping("/all")
    public Result<List<WorkCenterVO>> listAll() {
        return Result.ok(workCenterService.listAll());
    }

    /** 新建工作中心 */
    @Operation(summary = "新建工作中心")
    @RequirePerm("mrp:capacity:add")
    @OperLog(module = "生产制造", operation = "新增工作中心")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody WorkCenterForm form) {
        return Result.ok(workCenterService.create(form));
    }

    /** 修改工作中心 */
    @Operation(summary = "修改工作中心")
    @RequirePerm("mrp:capacity:edit")
    @OperLog(module = "生产制造", operation = "修改工作中心")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody WorkCenterForm form) {
        workCenterService.update(id, form);
        return Result.ok();
    }

    /** 删除工作中心（逻辑删除） */
    @Operation(summary = "删除工作中心")
    @RequirePerm("mrp:capacity:delete")
    @OperLog(module = "生产制造", operation = "删除工作中心")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workCenterService.delete(id);
        return Result.ok();
    }
}
