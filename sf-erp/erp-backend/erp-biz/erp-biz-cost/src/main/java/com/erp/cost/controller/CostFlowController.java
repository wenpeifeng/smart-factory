package com.erp.cost.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.cost.dto.form.HookReceiveForm;
import com.erp.cost.dto.request.CostFlowQuery;
import com.erp.cost.dto.vo.CostFlowVO;
import com.erp.cost.dto.vo.HookResultVO;
import com.erp.cost.dto.vo.SyncResultVO;
import com.erp.cost.service.CostFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 存货计价流水接口：流水分页 / 期间重算（sync）/ 业务补录（hook）。
 */
@Tag(name = "存货计价")
@RestController
@RequestMapping("/cost")
@RequiredArgsConstructor
public class CostFlowController {

    private final CostFlowService flowService;

    /** 计价流水分页（筛选：period/materialId/bizType/keyword） */
    @Operation(summary = "计价流水分页")
    @RequirePerm("cost:flow:list")
    @GetMapping("/flows")
    public Result<PageResult<CostFlowVO>> page(@ModelAttribute CostFlowQuery query,
                                               @RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(flowService.page(query, pageNum, pageSize));
    }

    /** 从 inv_movement 重算指定期间计价流水（初始化/补账；from 可空） */
    @Operation(summary = "按期间重算计价流水")
    @RequirePerm("cost:flow:edit")
    @OperLog(module = "成本管理", operation = "同步成本流水")
    @Idempotent
    @PostMapping("/sync")
    public Result<SyncResultVO> sync(@RequestParam(required = false) String from,
                                     @RequestParam String to) {
        return Result.ok(flowService.sync(from, to));
    }

    /** 手工补录一笔业务计价（入库带金额/出库按移动加权价） */
    @Operation(summary = "手工补录业务计价")
    @RequirePerm("cost:flow:add")
    @OperLog(module = "成本管理", operation = "接收成本流水Hook")
    @Idempotent
    @PostMapping("/hooks/receive")
    public Result<HookResultVO> receive(@Valid @RequestBody HookReceiveForm form) {
        return Result.ok(flowService.receive(form));
    }
}
