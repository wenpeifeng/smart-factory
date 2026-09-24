package com.erp.qc.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.qc.dto.form.QcTemplateForm;
import com.erp.qc.dto.query.QcTemplateQuery;
import com.erp.qc.dto.vo.QcTemplateVO;
import com.erp.qc.service.QcTemplateService;
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
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 检验模板接口。
 * <p>
 * 模板 = 检验类型(IQC/IPQC/FQC/OQC) + 适用物料分类 + 检验项目子表；
 * 检验单明细为模板快照，模板修改/删除不追溯影响历史检验单。
 */
@Tag(name = "质量管理-检验模板")
@RestController
@RequestMapping("/qc/templates")
@RequiredArgsConstructor
public class QcTemplateController {

    private final QcTemplateService templateService;

    /** 分页查询模板（关键词/检验类型/状态） */
    @Operation(summary = "分页查询检验模板")
    @RequirePerm("qc:template:list")
    @GetMapping
    public Result<PageResult<QcTemplateVO>> page(QcTemplateQuery query) {
        return Result.ok(templateService.page(query));
    }

    /** 模板详情（含检验项目子表） */
    @Operation(summary = "检验模板详情")
    @RequirePerm("qc:template:list")
    @GetMapping("/{id}")
    public Result<QcTemplateVO> detail(@PathVariable Long id) {
        return Result.ok(templateService.getDetail(id));
    }

    /** 新增模板 */
    @Operation(summary = "新增检验模板")
    @RequirePerm("qc:template:add")
    @OperLog(module = "质量管理", operation = "新增质检模板")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody QcTemplateForm form) {
        return Result.ok(templateService.create(form));
    }

    /** 修改模板（检验项目整体覆盖） */
    @Operation(summary = "修改检验模板")
    @RequirePerm("qc:template:edit")
    @OperLog(module = "质量管理", operation = "修改质检模板")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody QcTemplateForm form) {
        templateService.update(id, form);
        return Result.ok();
    }

    /** 删除模板（逻辑删） */
    @Operation(summary = "删除检验模板")
    @RequirePerm("qc:template:delete")
    @OperLog(module = "质量管理", operation = "删除质检模板")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return Result.ok();
    }
}
