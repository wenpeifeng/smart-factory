package com.erp.basedata.controller;

import com.erp.basedata.dto.form.PriceAuditForm;
import com.erp.basedata.dto.form.PriceSpecialForm;
import com.erp.basedata.dto.vo.PriceSpecialVO;
import com.erp.basedata.service.SalPriceSpecialService;
import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;
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

/**
 * 特价审批接口（价格中心-特价审批 Tab，权限挂销售口径 sales:priceSpecial:*）。
 * <p>
 * 申请提交后自动尝试挂 workflow（bizType=sal_price_special）；
 * 无启用流程时由管理员经 approve/reject 简单审批。
 */
@Tag(name = "基础资料-特价审批")
@RestController
@RequestMapping("/basedata/price-special")
@RequiredArgsConstructor
public class PriceSpecialController {

    private final SalPriceSpecialService specialService;

    /** 特价申请分页 */
    @Operation(summary = "特价申请分页")
    @RequirePerm("sales:priceSpecial:list")
    @GetMapping
    public Result<PageResult<PriceSpecialVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Integer status) {
        return Result.ok(specialService.page(pageNum, pageSize, customerId, materialId, status));
    }

    /** 提交特价申请（自动尝试挂审批流，无流程转管理员审批） */
    @Operation(summary = "提交特价申请")
    @RequirePerm("sales:priceSpecial:add")
    @OperLog(module = "特价审批", operation = "提交特价申请")
    @Idempotent
    @PostMapping
    public Result<Long> apply(@Valid @RequestBody PriceSpecialForm form) {
        return Result.ok(specialService.apply(form));
    }

    /** 管理员通过（0→10；审批流在途记录会被惰性同步，此处亦可人工兜底） */
    @Operation(summary = "通过特价申请")
    @RequirePerm("sales:priceSpecial:audit")
    @OperLog(module = "特价审批", operation = "通过特价申请")
    @Idempotent
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, @Valid @RequestBody(required = false) PriceAuditForm form) {
        specialService.approve(id, form == null ? null : form.getRemark());
        return Result.ok();
    }

    /** 管理员驳回（0→20） */
    @Operation(summary = "驳回特价申请")
    @RequirePerm("sales:priceSpecial:audit")
    @OperLog(module = "特价审批", operation = "驳回特价申请")
    @Idempotent
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody(required = false) PriceAuditForm form) {
        specialService.reject(id, form == null ? null : form.getRemark());
        return Result.ok();
    }

    /** 删除申请（仅待审/驳回可删） */
    @Operation(summary = "删除特价申请")
    @RequirePerm("sales:priceSpecial:delete")
    @OperLog(module = "特价审批", operation = "删除特价申请")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        specialService.delete(id);
        return Result.ok();
    }
}
