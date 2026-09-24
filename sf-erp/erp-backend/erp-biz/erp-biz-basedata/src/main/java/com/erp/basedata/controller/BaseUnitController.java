package com.erp.basedata.controller;

import com.erp.basedata.dto.form.BaseUnitForm;
import com.erp.basedata.dto.vo.BaseUnitVO;
import com.erp.basedata.service.BaseUnitService;
import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
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
 * 计量单位接口。
 * <p>Controller 只做参数接收/校验/组装响应，业务逻辑在 {@link BaseUnitService}。
 */
@Tag(name = "基础资料-计量单位")
@RestController
@RequestMapping("/basedata/units")
@RequiredArgsConstructor
public class BaseUnitController {

    private final BaseUnitService unitService;

    /** 分页查询计量单位 */
    @Operation(summary = "分页查询计量单位")
    @RequirePerm("basedata:unit:list")
    @GetMapping
    public Result<PageResult<BaseUnitVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.ok(unitService.page(pageNum, pageSize, keyword));
    }

    /** 启用单位列表（下拉选择用） */
    @Operation(summary = "启用单位列表(下拉)")
    @GetMapping("/enabled")
    public Result<List<BaseUnitVO>> listEnabled() {
        return Result.ok(unitService.listEnabled());
    }

    /** 单位详情 */
    @Operation(summary = "单位详情")
    @RequirePerm("basedata:unit:list")
    @GetMapping("/{id}")
    public Result<BaseUnitVO> getById(@PathVariable Long id) {
        return Result.ok(unitService.getById(id));
    }

    /** 新增单位 */
    @Operation(summary = "新增计量单位")
    @RequirePerm("basedata:unit:add")
    @OperLog(module = "计量单位", operation = "新增计量单位")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody BaseUnitForm form) {
        return Result.ok(unitService.create(form));
    }

    /** 修改单位 */
    @Operation(summary = "修改计量单位")
    @RequirePerm("basedata:unit:edit")
    @OperLog(module = "计量单位", operation = "修改计量单位")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BaseUnitForm form) {
        unitService.update(id, form);
        return Result.ok();
    }

    /** 删除单位（被物料引用抛 DATA_IN_USE） */
    @Operation(summary = "删除计量单位")
    @RequirePerm("basedata:unit:delete")
    @OperLog(module = "计量单位", operation = "删除计量单位")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        unitService.delete(id);
        return Result.ok();
    }
}
