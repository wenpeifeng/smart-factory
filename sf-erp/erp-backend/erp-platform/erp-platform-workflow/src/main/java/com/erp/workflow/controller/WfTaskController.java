package com.erp.workflow.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.workflow.dto.form.ApproveForm;
import com.erp.workflow.dto.query.WfTaskQuery;
import com.erp.workflow.dto.vo.WfTaskVO;
import com.erp.workflow.service.WorkflowService;
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
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

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

    /** 任务详情（移动端审批页展示：单据/金额/意见历史） */
    @Operation(summary = "任务详情")
    @RequirePerm("workflow:todo:list")
    @GetMapping("/tasks/{id}/detail")
    public Result<com.erp.workflow.dto.vo.WfTaskDetailVO> detail(@PathVariable Long id) {
        return Result.ok(workflowService.taskDetail(id));
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
