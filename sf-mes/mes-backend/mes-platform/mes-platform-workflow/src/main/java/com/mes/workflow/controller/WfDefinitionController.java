package com.mes.workflow.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.workflow.dto.form.StatusForm;
import com.mes.workflow.dto.form.WfDefinitionForm;
import com.mes.workflow.dto.query.WfDefinitionQuery;
import com.mes.workflow.dto.vo.WfDefinitionVO;
import com.mes.workflow.service.WfDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.system.annotation.OperLog;

/**
 * 流程定义接口。
 * <p>
 * 定义 + 节点子表整体提交/返回；启停用仅影响新发起，在途实例不受影响。
 */
@Tag(name = "流程定义")
@RestController
@RequestMapping("/workflow/defs")
@RequiredArgsConstructor
public class WfDefinitionController {

    private final WfDefinitionService definitionService;

    /** 分页查询流程定义（关键词/单据类型/状态） */
    @Operation(summary = "分页查询流程定义")
    @RequirePerm("workflow:def:list")
    @GetMapping
    public Result<PageResult<WfDefinitionVO>> page(@ModelAttribute WfDefinitionQuery query) {
        return Result.ok(definitionService.page(query));
    }

    /** 定义详情（含节点子表） */
    @Operation(summary = "流程定义详情")
    @RequirePerm("workflow:def:list")
    @GetMapping("/{id}")
    public Result<WfDefinitionVO> detail(@PathVariable Long id) {
        return Result.ok(definitionService.detail(id));
    }

    /** 新增流程定义（含节点） */
    @Operation(summary = "新增流程定义")
    @RequirePerm("workflow:def:add")
    @OperLog(module = "工作流", operation = "新增流程定义")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody WfDefinitionForm form) {
        return Result.ok(definitionService.create(form));
    }

    /** 修改流程定义（节点整体覆盖） */
    @Operation(summary = "修改流程定义")
    @RequirePerm("workflow:def:edit")
    @OperLog(module = "工作流", operation = "修改流程定义")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody WfDefinitionForm form) {
        definitionService.update(id, form);
        return Result.ok();
    }

    /** 删除流程定义（存在审批中实例时禁止） */
    @Operation(summary = "删除流程定义")
    @RequirePerm("workflow:def:delete")
    @OperLog(module = "工作流", operation = "删除流程定义")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        definitionService.delete(id);
        return Result.ok();
    }

    /** 启用/停用流程定义 */
    @Operation(summary = "启用停用流程定义")
    @RequirePerm("workflow:def:edit")
    @OperLog(module = "工作流", operation = "启停流程定义")
    @Idempotent
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        definitionService.updateStatus(id, form.getStatus());
        return Result.ok();
    }
}
