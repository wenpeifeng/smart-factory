package com.erp.mrp.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.mrp.dto.form.OperationForm;
import com.erp.mrp.dto.vo.OperationVO;
import com.erp.mrp.service.MrpOperationService;
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
 * 工序定义接口：CRUD + 选择器列表（编码 OP+流水自动生成）。
 */
@Tag(name = "生产制造-工序定义")
@RestController
@RequestMapping("/mrp/operations")
@RequiredArgsConstructor
public class MrpOperationController {

    private final MrpOperationService operationService;

    /** 分页查询工序 */
    @Operation(summary = "分页查询工序")
    @RequirePerm("mrp:routing:list")
    @GetMapping
    public Result<PageResult<OperationVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(operationService.page(pageNum, pageSize, keyword, status));
    }

    /** 全部工序（选择器） */
    @Operation(summary = "工序选择器数据")
    @RequirePerm("mrp:routing:list")
    @GetMapping("/all")
    public Result<List<OperationVO>> listAll() {
        return Result.ok(operationService.listAll());
    }

    /** 新建工序 */
    @Operation(summary = "新建工序")
    @RequirePerm("mrp:routing:add")
    @OperLog(module = "生产制造", operation = "新增工序")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody OperationForm form) {
        return Result.ok(operationService.create(form));
    }

    /** 修改工序 */
    @Operation(summary = "修改工序")
    @RequirePerm("mrp:routing:edit")
    @OperLog(module = "生产制造", operation = "修改工序")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody OperationForm form) {
        operationService.update(id, form);
        return Result.ok();
    }

    /** 删除工序（逻辑删除） */
    @Operation(summary = "删除工序")
    @RequirePerm("mrp:routing:delete")
    @OperLog(module = "生产制造", operation = "删除工序")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        operationService.delete(id);
        return Result.ok();
    }
}
