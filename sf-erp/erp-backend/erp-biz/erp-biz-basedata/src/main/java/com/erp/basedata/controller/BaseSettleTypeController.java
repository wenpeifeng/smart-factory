package com.erp.basedata.controller;

import com.erp.basedata.dto.form.BaseSettleTypeForm;
import com.erp.basedata.dto.vo.BaseSettleTypeVO;
import com.erp.basedata.service.BaseSettleTypeService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 结算方式接口（数据量小，全量列表为主）。
 */
@Tag(name = "基础资料-结算方式")
@RestController
@RequestMapping("/basedata/settle-types")
@RequiredArgsConstructor
public class BaseSettleTypeController {

    private final BaseSettleTypeService settleTypeService;

    /** 全部结算方式（含停用，管理用） */
    @Operation(summary = "全部结算方式")
    @RequirePerm("basedata:settleType:list")
    @GetMapping
    public Result<List<BaseSettleTypeVO>> listAll() {
        return Result.ok(settleTypeService.listAll());
    }

    /** 启用结算方式（下拉用） */
    @Operation(summary = "启用结算方式(下拉)")
    @GetMapping("/enabled")
    public Result<List<BaseSettleTypeVO>> listEnabled() {
        return Result.ok(settleTypeService.listEnabled());
    }

    /** 结算方式详情 */
    @Operation(summary = "结算方式详情")
    @RequirePerm("basedata:settleType:list")
    @GetMapping("/{id}")
    public Result<BaseSettleTypeVO> getById(@PathVariable Long id) {
        return Result.ok(settleTypeService.getById(id));
    }

    /** 新增结算方式 */
    @Operation(summary = "新增结算方式")
    @RequirePerm("basedata:settleType:add")
    @OperLog(module = "结算方式", operation = "新增结算方式")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody BaseSettleTypeForm form) {
        return Result.ok(settleTypeService.create(form));
    }

    /** 修改结算方式 */
    @Operation(summary = "修改结算方式")
    @RequirePerm("basedata:settleType:edit")
    @OperLog(module = "结算方式", operation = "修改结算方式")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BaseSettleTypeForm form) {
        settleTypeService.update(id, form);
        return Result.ok();
    }

    /** 删除结算方式（被客商引用抛 DATA_IN_USE） */
    @Operation(summary = "删除结算方式")
    @RequirePerm("basedata:settleType:delete")
    @OperLog(module = "结算方式", operation = "删除结算方式")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        settleTypeService.delete(id);
        return Result.ok();
    }
}
