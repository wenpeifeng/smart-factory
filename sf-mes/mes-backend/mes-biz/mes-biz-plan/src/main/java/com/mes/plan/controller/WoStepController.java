package com.mes.plan.controller;

import com.mes.common.result.Result;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.plan.dto.vo.WoStepVO;
import com.mes.plan.service.WoStepService;
import com.mes.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工单工序任务接口（派工 + 查询）。
 */
@Tag(name = "计划-工单工序")
@RestController
@RequestMapping("/plan/steps")
@RequiredArgsConstructor
public class WoStepController {

    private final WoStepService woStepService;

    /**
     * 查询工单工序列表。
     */
    @Operation(summary = "工单工序列表")
    @RequirePerm("plan:workorder:list")
    @GetMapping("/workorder/{workorderId}")
    public Result<List<WoStepVO>> listByWorkorder(@PathVariable Long workorderId) {
        return Result.ok(woStepService.listByWorkorder(workorderId));
    }

    /**
     * 工序详情。
     */
    @Operation(summary = "工序详情")
    @RequirePerm("plan:workorder:list")
    @GetMapping("/{id}")
    public Result<WoStepVO> detail(@PathVariable Long id) {
        return Result.ok(woStepService.getById(id));
    }

    /**
     * 派工（指定派工对象）。
     */
    @Operation(summary = "工序派工")
    @RequirePerm("plan:step:dispatch")
    @OperLog(module = "工单工序", operation = "工序派工")
    @Idempotent
    @PutMapping("/{id}/dispatch")
    public Result<Void> dispatch(@PathVariable Long id,
                                 @RequestParam Long assigneeId,
                                 @RequestParam String assigneeName) {
        woStepService.dispatch(id, assigneeId, assigneeName);
        return Result.ok();
    }
}
