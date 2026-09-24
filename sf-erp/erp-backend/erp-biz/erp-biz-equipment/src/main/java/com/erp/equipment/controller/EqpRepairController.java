package com.erp.equipment.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.equipment.dto.form.RepairAcceptForm;
import com.erp.equipment.dto.form.RepairCompleteForm;
import com.erp.equipment.dto.form.RepairReportForm;
import com.erp.equipment.dto.request.RepairQuery;
import com.erp.equipment.dto.vo.RepairDetailVO;
import com.erp.equipment.dto.vo.RepairVO;
import com.erp.equipment.service.EqpRepairService;
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

import java.util.Map;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 维修工单接口（报修 → 接单 → 完工 → 关闭）。
 * <p>
 * 完工会按备件明细扣库存（EQP_OUT 流水），库存不足整体回滚。
 */
@Tag(name = "设备管理-维修工单")
@RestController
@RequestMapping("/equipment/repairs")
@RequiredArgsConstructor
public class EqpRepairController {

    private final EqpRepairService repairService;

    /** 工单分页（状态/设备/工单号或设备名关键词） */
    @Operation(summary = "维修工单分页查询")
    @RequirePerm("equipment:repair:list")
    @GetMapping
    public Result<PageResult<RepairVO>> page(@ModelAttribute RepairQuery query) {
        return Result.ok(repairService.page(query));
    }

    /** 工单详情（头 + 备件明细） */
    @Operation(summary = "维修工单详情")
    @RequirePerm("equipment:repair:list")
    @GetMapping("/{id}")
    public Result<RepairDetailVO> detail(@PathVariable Long id) {
        return Result.ok(repairService.detail(id));
    }

    /** 报修：创建待维修工单（WX+日期+流水） */
    @Operation(summary = "报修")
    @RequirePerm("equipment:repair:add")
    @OperLog(module = "设备管理", operation = "上报维修")
    @Idempotent
    @PostMapping
    public Result<Map<String, Long>> report(@Valid @RequestBody RepairReportForm form) {
        return Result.ok(Map.of("id", repairService.report(form)));
    }

    /** 接单：待维修 → 维修中（登记维修人，设备联动置维修中） */
    @Operation(summary = "接单")
    @RequirePerm("equipment:repair:audit")
    @OperLog(module = "设备管理", operation = "受理维修")
    @Idempotent
    @PutMapping("/{id}/accept")
    public Result<Void> accept(@PathVariable Long id, @Valid @RequestBody RepairAcceptForm form) {
        repairService.accept(id, form);
        return Result.ok();
    }

    /** 完工：维修中 → 已修复（备件出库，库存不足整体回滚） */
    @Operation(summary = "完工")
    @RequirePerm("equipment:repair:audit")
    @OperLog(module = "设备管理", operation = "完成维修")
    @Idempotent
    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id, @Valid @RequestBody RepairCompleteForm form) {
        repairService.complete(id, form);
        return Result.ok();
    }

    /** 关闭：已修复 → 已关闭（归档） */
    @Operation(summary = "关闭工单")
    @RequirePerm("equipment:repair:audit")
    @OperLog(module = "设备管理", operation = "关闭维修单")
    @Idempotent
    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        repairService.close(id);
        return Result.ok();
    }
}
