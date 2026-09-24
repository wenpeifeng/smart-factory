package com.mes.quality.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.quality.dto.form.QcTemplateForm;
import com.mes.quality.dto.vo.QcTemplateVO;
import com.mes.quality.service.QcTemplateService;
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
 * 检验模板接口。
 */
@Tag(name = "质量-检验模板")
@RestController
@RequestMapping("/quality/templates")
@RequiredArgsConstructor
public class QcTemplateController {

    private final QcTemplateService templateService;

    /**
     * 分页查询模板。
     */
    @Operation(summary = "分页查询检验模板")
    @RequirePerm("quality:template:list")
    @GetMapping
    public Result<PageResult<QcTemplateVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String inspectType) {
        return Result.ok(templateService.page(pageNum, pageSize, keyword, inspectType));
    }

    /**
     * 模板详情。
     */
    @Operation(summary = "检验模板详情")
    @RequirePerm("quality:template:list")
    @GetMapping("/{id}")
    public Result<QcTemplateVO> detail(@PathVariable Long id) {
        return Result.ok(templateService.detail(id));
    }

    /**
     * 创建模板。
     */
    @Operation(summary = "创建检验模板")
    @RequirePerm("quality:template:add")
    @OperLog(module = "检验模板", operation = "创建检验模板")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody QcTemplateForm form) {
        return Result.ok(templateService.create(form));
    }

    /**
     * 修改模板。
     */
    @Operation(summary = "修改检验模板")
    @RequirePerm("quality:template:edit")
    @OperLog(module = "检验模板", operation = "修改检验模板")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody QcTemplateForm form) {
        templateService.update(id, form);
        return Result.ok();
    }

    /**
     * 删除模板。
     */
    @Operation(summary = "删除检验模板")
    @RequirePerm("quality:template:delete")
    @OperLog(module = "检验模板", operation = "删除检验模板")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return Result.ok();
    }

    /**
     * 启用/停用模板。
     */
    @Operation(summary = "启停检验模板")
    @RequirePerm("quality:template:edit")
    @OperLog(module = "检验模板", operation = "启停检验模板")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        templateService.changeStatus(id, status);
        return Result.ok();
    }
}
