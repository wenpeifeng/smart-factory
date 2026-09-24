package com.erp.cost.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.cost.dto.form.OverheadForm;
import com.erp.cost.dto.request.WorkOrderQuery;
import com.erp.cost.dto.vo.WorkOrderCostDetailVO;
import com.erp.cost.dto.vo.WorkOrderCostRowVO;
import com.erp.cost.service.CostWorkOrderService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 工单成本归集接口：料工费三块归集 + 制造费用手工分摊。
 */
@Tag(name = "工单成本归集")
@RestController
@RequestMapping("/cost/work-orders")
@RequiredArgsConstructor
public class CostWorkOrderController {

    private final CostWorkOrderService workOrderService;

    /** 工单成本分页（料工费 + 单位成本） */
    @Operation(summary = "工单成本分页")
    @RequirePerm("cost:workorder:list")
    @GetMapping
    public Result<PageResult<WorkOrderCostRowVO>> page(@ModelAttribute WorkOrderQuery query,
                                                       @RequestParam(defaultValue = "1") long pageNum,
                                                       @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(workOrderService.page(query, pageNum, pageSize));
    }

    /** 工单成本详情（领料明细/工资明细/费用分摊/三费合计） */
    @Operation(summary = "工单成本详情")
    @RequirePerm("cost:workorder:list")
    @GetMapping("/{id}")
    public Result<WorkOrderCostDetailVO> detail(@PathVariable Long id) {
        return Result.ok(workOrderService.detail(id));
    }

    /** 制造费用分摊录入（整体覆盖费/工时） */
    @Operation(summary = "制造费用分摊录入")
    @RequirePerm("cost:workorder:edit")
    @OperLog(module = "成本管理", operation = "保存工单费用分摊")
    @Idempotent
    @PostMapping("/{id}/overhead")
    public Result<Long> saveOverhead(@PathVariable Long id, @Valid @RequestBody OverheadForm form) {
        return Result.ok(workOrderService.saveOverhead(id, form));
    }
}
