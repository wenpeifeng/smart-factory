package com.mes.workflow.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.workflow.dto.form.ApproveForm;
import com.mes.workflow.dto.query.WfTaskQuery;
import com.mes.workflow.dto.vo.WfTaskVO;
import com.mes.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.system.annotation.OperLog;

/**
 * 审批任务接口（我的待办/已办 + 通过/驳回）。
 * <p>
 * 处理权限：仅任务审批人本人；任务推进用条件 UPDATE 防并发双处理。
 */
@Tag(name = "审批任务")
@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WfTaskController {

    private final WorkflowService workflowService;

    /** 我的待办分页 */
    @Operation(summary = "我的待办")
    @RequirePerm("workflow:todo:list")
    @GetMapping("/todo")
    public Result<PageResult<WfTaskVO>> todo(@ModelAttribute WfTaskQuery query) {
        return Result.ok(workflowService.todoPage(query));
    }

    /** 我的已办分页 */
    @Operation(summary = "我的已办")
    @RequirePerm("workflow:todo:list")
    @GetMapping("/done")
    public Result<PageResult<WfTaskVO>> done(@ModelAttribute WfTaskQuery query) {
        return Result.ok(workflowService.donePage(query));
    }

    /** 审批通过（最后节点通过后实例 AGREED） */
    @Operation(summary = "审批通过")
    @RequirePerm("workflow:todo:audit")
    @OperLog(module = "工作流", operation = "审批通过")
    @Idempotent
    @PostMapping("/tasks/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, @Valid @RequestBody ApproveForm form) {
        workflowService.approve(id, form.getOpinion());
        return Result.ok();
    }

    /** 审批驳回（实例整体 REJECTED） */
    @Operation(summary = "审批驳回")
    @RequirePerm("workflow:todo:audit")
    @OperLog(module = "工作流", operation = "审批驳回")
    @Idempotent
    @PostMapping("/tasks/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody ApproveForm form) {
        workflowService.reject(id, form.getOpinion());
        return Result.ok();
    }
}
