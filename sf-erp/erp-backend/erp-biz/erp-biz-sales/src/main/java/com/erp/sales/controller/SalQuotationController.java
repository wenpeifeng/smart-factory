package com.erp.sales.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.sales.dto.form.SalQuotationForm;
import com.erp.sales.dto.form.SalQuotationToOrderForm;
import com.erp.sales.dto.vo.QuotationMarginBundle;
import com.erp.sales.dto.vo.SalQuotationDetailBundle;
import com.erp.sales.dto.vo.SalQuotationVO;
import com.erp.sales.service.SalQuotationService;
import com.erp.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 销售报价单接口。
 * <p>
 * 状态机：0草稿 →(提交报价)→ 10已报价 →(下推)→ 20已转订单；
 * 已报价过有效期动态显示 30已过期；0/10 →(作废)→ 40已作废。
 */
@Tag(name = "销售报价单")
@RestController
@RequestMapping("/sales/quotations")
@RequiredArgsConstructor
public class SalQuotationController {

    private final SalQuotationService quotationService;

    /** 分页查询报价单（status=30 查已过期；支持客户/有效期筛选） */
    @Operation(summary = "分页查询销售报价单")
    @RequirePerm("sales:quotation:list")
    @GetMapping
    public Result<PageResult<SalQuotationVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validTo) {
        return Result.ok(quotationService.page(pageNum, pageSize, keyword, customerId,
                status, validFrom, validTo));
    }

    /** 查询报价单详情（表头 + 明细行，含成本参考快照） */
    @Operation(summary = "查询销售报价单详情")
    @RequirePerm("sales:quotation:list")
    @GetMapping("/{id}")
    public Result<SalQuotationDetailBundle> detail(@PathVariable Long id) {
        return Result.ok(quotationService.getDetail(id));
    }

    /** 新增报价单（草稿态） */
    @Operation(summary = "新增销售报价单")
    @RequirePerm("sales:quotation:add")
    @OperLog(module = "销售管理", operation = "新增销售报价单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SalQuotationForm form) {
        return Result.ok(quotationService.create(form));
    }

    /** 修改报价单（仅草稿） */
    @Operation(summary = "修改销售报价单")
    @RequirePerm("sales:quotation:edit")
    @OperLog(module = "销售管理", operation = "修改销售报价单")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SalQuotationForm form) {
        quotationService.update(id, form);
        return Result.ok();
    }

    /** 删除报价单（仅草稿，逻辑删除） */
    @Operation(summary = "删除销售报价单")
    @RequirePerm("sales:quotation:delete")
    @OperLog(module = "销售管理", operation = "删除销售报价单")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        quotationService.delete(id);
        return Result.ok();
    }

    /** 提交报价：0草稿 → 10已报价 */
    @Operation(summary = "提交销售报价")
    @RequirePerm("sales:quotation:audit")
    @OperLog(module = "销售管理", operation = "提交销售报价")
    @Idempotent
    @PutMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        quotationService.submit(id);
        return Result.ok();
    }

    /** 作废报价：0草稿/10已报价 → 40已作废（终态） */
    @Operation(summary = "作废销售报价")
    @RequirePerm("sales:quotation:audit")
    @OperLog(module = "销售管理", operation = "作废销售报价")
    @Idempotent
    @PutMapping("/{id}/void")
    public Result<Void> voidQuotation(@PathVariable Long id) {
        quotationService.voidQuotation(id);
        return Result.ok();
    }

    /** 行毛利率视图：(报价−成本)/报价，低于警戒线(20%)的行标记 warn */
    @Operation(summary = "报价单行毛利率视图")
    @RequirePerm("sales:quotation:list")
    @GetMapping("/{id}/margin")
    public Result<QuotationMarginBundle> margin(@PathVariable Long id) {
        return Result.ok(quotationService.margin(id));
    }

    /** 下推生成销售订单草稿（校验已报价且未过期；单价=报价×(1−折扣)） */
    @Operation(summary = "报价单下推销售订单")
    @RequirePerm("sales:quotation:toOrder")
    @OperLog(module = "销售管理", operation = "报价单下推销售订单")
    @Idempotent
    @PostMapping("/{id}/to-order")
    public Result<Long> toOrder(@PathVariable Long id,
                                @Valid @RequestBody(required = false) SalQuotationToOrderForm form) {
        return Result.ok(quotationService.toOrder(id,
                form == null ? new SalQuotationToOrderForm() : form));
    }
}
