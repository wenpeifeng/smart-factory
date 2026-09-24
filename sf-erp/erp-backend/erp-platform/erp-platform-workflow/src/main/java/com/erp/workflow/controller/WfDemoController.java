package com.erp.workflow.controller;

import com.erp.common.result.Result;
import com.erp.workflow.dto.form.DemoStartForm;
import com.erp.workflow.dto.vo.StartResultVO;
import com.erp.workflow.service.WfWarnService;
import com.erp.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 引擎联调接口（测试专用，一期单据接入后由单据模块的 startFlow 调用取代）。
 * <p>
 * - POST /workflow/demo/start：不落业务表，用时间戳模拟 bizId 发起流程，
 *   验证"定义匹配→条件路由→待办派发→isApproved"引擎闭环；
 * - POST /workflow/warn/run：手动触发一次预警扫描（免等整点调度，验证预警链路）。
 */
@Tag(name = "工作流联调")
@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WfDemoController {

    private final WorkflowService workflowService;
    private final WfWarnService warnService;

    /** 模拟发起流程（验证引擎，不落业务表） */
    @Operation(summary = "演示发起流程(联调用)")
    @RequirePerm("workflow:todo:add")
    @OperLog(module = "工作流", operation = "演示发起流程")
    @Idempotent
    @PostMapping("/demo/start")
    public Result<Map<String, Object>> demoStart(@Valid @RequestBody DemoStartForm form) {
        // 时间戳模拟单据 ID，单号 DEMO+时分秒，变量 amount 供条件路由
        long fakeBizId = System.currentTimeMillis();
        String fakeBizNo = "DEMO" + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"));
        Map<String, Object> vars = form.getAmount() == null
                ? Map.of()
                : Map.of("amount", form.getAmount());
        StartResultVO start = workflowService.startFlow(
                form.getBizType(), fakeBizId, fakeBizNo, vars);
        boolean approved = workflowService.isApproved(form.getBizType(), fakeBizId);
        return Result.ok(Map.of(
                "bizId", fakeBizId,
                "bizNo", fakeBizNo,
                "start", start,
                "approved", approved));
    }

    /** 手动触发预警扫描（免等整点调度） */
    @Operation(summary = "手动触发预警扫描(联调用)")
    @RequirePerm("workflow:message:edit")
    @OperLog(module = "工作流", operation = "触发预警扫描")
    @Idempotent
    @PostMapping("/warn/run")
    public Result<Integer> runWarn() {
        return Result.ok(warnService.scanOnce());
    }
}
