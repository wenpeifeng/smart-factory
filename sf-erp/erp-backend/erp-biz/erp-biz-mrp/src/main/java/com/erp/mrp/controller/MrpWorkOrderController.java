package com.erp.mrp.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.mrp.dto.form.FinishForm;
import com.erp.mrp.dto.form.WorkOrderForm;
import com.erp.mrp.dto.vo.WorkOrderVO;
import com.erp.mrp.service.MrpWorkOrderService;
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
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 生产订单接口：CRUD、下达（校验BOM有效）、完工入库、关闭。
 */
@Tag(name = "生产制造-生产订单")
@RestController
@RequestMapping("/mrp/work-orders")
@RequiredArgsConstructor
public class MrpWorkOrderController {

    private final MrpWorkOrderService workOrderService;

    /** 分页查询生产订单 */
    @Operation(summary = "分页查询生产订单")
    @RequirePerm("mrp:workorder:list")
    @GetMapping
    public Result<PageResult<WorkOrderVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(workOrderService.page(pageNum, pageSize, keyword, status));
    }

    /** 订单详情 */
    @Operation(summary = "生产订单详情")
    @RequirePerm("mrp:workorder:list")
    @GetMapping("/{id}")
    public Result<WorkOrderVO> detail(@PathVariable Long id) {
        return Result.ok(workOrderService.detail(id));
    }

    /** 手工新建草稿订单 */
    @Operation(summary = "新建生产订单")
    @RequirePerm("mrp:workorder:add")
    @OperLog(module = "生产制造", operation = "新增工单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody WorkOrderForm form) {
        return Result.ok(workOrderService.create(form));
    }

    /** 修改草稿订单 */
    @Operation(summary = "修改生产订单")
    @RequirePerm("mrp:workorder:edit")
    @OperLog(module = "生产制造", operation = "修改工单")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody WorkOrderForm form) {
        workOrderService.update(id, form);
        return Result.ok();
    }

    /** 删除草稿订单 */
    @Operation(summary = "删除生产订单")
    @RequirePerm("mrp:workorder:delete")
    @OperLog(module = "生产制造", operation = "删除工单")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workOrderService.delete(id);
        return Result.ok();
    }

    /** 下达订单（三段式：免审直过/提交审批/审批通过后执行下达；校验 BOM 有效） */
    @Operation(summary = "下达生产订单")
    @RequirePerm("mrp:workorder:audit")
    @OperLog(module = "生产制造", operation = "下达工单")
    @Idempotent
    @PostMapping("/{id}/release")
    public Result<String> release(@PathVariable Long id) {
        return Result.ok(workOrderService.release(id));
    }

    /** 完工入库（良品入库/不良登记） */
    @Operation(summary = "完工入库")
    @RequirePerm("mrp:workorder:audit")
    @OperLog(module = "生产制造", operation = "工单完工")
    @Idempotent
    @PostMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id, @Valid @RequestBody FinishForm form) {
        workOrderService.finish(id, form);
        return Result.ok();
    }

    /** 关闭订单 */
    @Operation(summary = "关闭生产订单")
    @RequirePerm("mrp:workorder:audit")
    @OperLog(module = "生产制造", operation = "关闭工单")
    @Idempotent
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        workOrderService.close(id);
        return Result.ok();
    }
}
