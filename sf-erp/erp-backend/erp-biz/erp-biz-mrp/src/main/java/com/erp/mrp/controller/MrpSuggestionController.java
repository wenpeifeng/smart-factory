package com.erp.mrp.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.mrp.dto.form.ToPurchaseBatchForm;
import com.erp.mrp.dto.form.ToPurchaseForm;
import com.erp.mrp.dto.form.ToWorkOrderForm;
import com.erp.mrp.dto.vo.SuggestionVO;
import com.erp.mrp.service.MrpSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
 * MRP 发补建议接口：结果分页 + 转生产订单 + 转采购订单（mrp → purchase 接线）。
 * <p>
 * 转采购生成采购订单草稿（供应商可空待补填、单价 0 待补、交期=建议日期），
 * 成功后建议 CONVERTED 并回写采购订单引用；重复转单被拒。
 */
@Tag(name = "生产制造-MRP建议")
@RestController
@RequestMapping("/mrp/suggestions")
@RequiredArgsConstructor
public class MrpSuggestionController {

    private final MrpSuggestionService suggestionService;

    /** 建议分页（批次/类型/状态过滤） */
    @Operation(summary = "建议分页查询")
    @RequirePerm("mrp:mrp:list")
    @GetMapping
    public Result<PageResult<SuggestionVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String planType,
            @RequestParam(required = false) String status) {
        return Result.ok(suggestionService.page(pageNum, pageSize, batchNo, planType, status));
    }

    /** 生产建议转生产订单（生成草稿单） */
    @Operation(summary = "建议转生产订单")
    @RequirePerm("mrp:workorder:add")
    @OperLog(module = "生产制造", operation = "MRP建议转工单")
    @Idempotent
    @PostMapping("/{id}/to-work-order")
    public Result<Long> toWorkOrder(@PathVariable Long id,
                                    @RequestBody(required = false) ToWorkOrderForm form) {
        return Result.ok(suggestionService.toWorkOrder(id, form));
    }

    /** 采购建议转采购订单（生成草稿单，供应商可空待补填） */
    @Operation(summary = "采购建议转采购订单")
    @RequirePerm("purchase:order:add")
    @OperLog(module = "生产制造", operation = "MRP建议转采购订单")
    @Idempotent
    @PostMapping("/{id}/to-purchase")
    public Result<Long> toPurchase(@PathVariable Long id,
                                   @RequestBody(required = false) ToPurchaseForm form) {
        return Result.ok(suggestionService.toPurchase(id, form));
    }

    /** 采购建议批量转采购订单（同供应商合并一张草稿单，每条建议一行明细） */
    @Operation(summary = "采购建议批量转采购订单")
    @RequirePerm("purchase:order:add")
    @OperLog(module = "生产制造", operation = "MRP建议批量转采购订单")
    @Idempotent
    @PostMapping("/to-purchase-batch")
    public Result<Long> toPurchaseBatch(@Valid @RequestBody ToPurchaseBatchForm form) {
        return Result.ok(suggestionService.toPurchaseBatch(form));
    }
}
