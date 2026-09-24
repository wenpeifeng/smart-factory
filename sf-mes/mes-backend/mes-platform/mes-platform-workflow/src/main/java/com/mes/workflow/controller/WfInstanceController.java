package com.mes.workflow.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.workflow.dto.form.StartFlowForm;
import com.mes.workflow.dto.query.WfInstanceQuery;
import com.mes.workflow.dto.vo.StartResultVO;
import com.mes.workflow.dto.vo.WfInstanceVO;
import com.mes.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 流程实例接口（发起 + 我发起的列表 + 撤回）。
 * <p>
 * 业务单据模块集成期统一改走 WorkflowService（见其 Javadoc），本接口供
 * 前端页面与联调使用。
 */
@Tag(name = "流程实例")
@RestController
@RequestMapping("/workflow/instances")
@RequiredArgsConstructor
public class WfInstanceController {

    private final WorkflowService workflowService;

    /** 发起流程（无匹配启用流程返回 SKIPPED） */
    @Operation(summary = "发起流程")
    @RequirePerm("workflow:todo:add")
    @OperLog(module = "工作流", operation = "发起流程")
    @Idempotent
    @PostMapping
    public Result<StartResultVO> start(@Valid @RequestBody StartFlowForm form) {
        return Result.ok(workflowService.startFlow(
                form.getBizType(), form.getBizId(), form.getBizNo(), form.getVars()));
    }

    /** 我发起的流程分页 */
    @Operation(summary = "我发起的流程")
    @RequirePerm("workflow:todo:list")
    @GetMapping
    public Result<PageResult<WfInstanceVO>> page(@ModelAttribute WfInstanceQuery query) {
        return Result.ok(workflowService.myInstancePage(query));
    }

    /** 撤回流程（仅发起人，仅运行中） */
    @Operation(summary = "撤回流程")
    @RequirePerm("workflow:todo:audit")
    @OperLog(module = "工作流", operation = "取消流程实例")
    @Idempotent
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        workflowService.cancel(id);
        return Result.ok();
    }
}
