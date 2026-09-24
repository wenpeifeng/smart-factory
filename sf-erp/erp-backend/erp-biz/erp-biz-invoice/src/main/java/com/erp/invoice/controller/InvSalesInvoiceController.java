package com.erp.invoice.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.invoice.dto.form.SalesInvoiceApplyForm;
import com.erp.invoice.dto.request.SalesInvoiceQuery;
import com.erp.invoice.dto.vo.SalesInvoiceVO;
import com.erp.invoice.dto.vo.ToBeInvoicedVO;
import com.erp.invoice.service.InvSalesInvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 销项发票接口：分页查询、开票申请（从销售出库单）、开票、红冲、客户待开票清单。
 */
@Tag(name = "发票管理-销项发票")
@RestController
@RequestMapping("/invoice/sales")
@RequiredArgsConstructor
public class InvSalesInvoiceController {

    private final InvSalesInvoiceService salesInvoiceService;

    /** 分页查询销项发票（客户/状态/关键词/开票日期区间） */
    @Operation(summary = "分页查询销项发票")
    @RequirePerm("invoice:sales:list")
    @GetMapping
    public Result<PageResult<SalesInvoiceVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(salesInvoiceService.page(
                new SalesInvoiceQuery(customerId, status, keyword, from, to), pageNum, pageSize));
    }

    /** 发票明细 */
    @Operation(summary = "销项发票明细")
    @RequirePerm("invoice:sales:list")
    @GetMapping("/{id}")
    public Result<SalesInvoiceVO> detail(@PathVariable Long id) {
        return Result.ok(salesInvoiceService.detail(id));
    }

    /** 开票申请：从销售出库单生成待开票（税率默认 13%） */
    @Operation(summary = "开票申请（从销售出库单）")
    @RequirePerm("invoice:sales:add")
    @OperLog(module = "发票管理", operation = "开具销售发票")
    @Idempotent
    @PostMapping("/from-order/{outstockId}")
    public Result<Long> applyFromOrder(@PathVariable Long outstockId,
                                       @Valid @RequestBody(required = false) SalesInvoiceApplyForm form) {
        return Result.ok(salesInvoiceService.applyFromOrder(outstockId, form));
    }

    /** 开票：待开票 → 已开票 */
    @Operation(summary = "开票")
    @RequirePerm("invoice:sales:audit")
    @OperLog(module = "发票管理", operation = "销售发票开票")
    @Idempotent
    @PostMapping("/{id}/issue")
    public Result<Void> issue(@PathVariable Long id) {
        salesInvoiceService.issue(id);
        return Result.ok();
    }

    /** 红冲：原票置已红冲，并生成负数红字发票关联原票号 */
    @Operation(summary = "红冲")
    @RequirePerm("invoice:sales:audit")
    @OperLog(module = "发票管理", operation = "冲销销售发票")
    @Idempotent
    @PostMapping("/{id}/reverse")
    public Result<Void> reverse(@PathVariable Long id) {
        salesInvoiceService.reverse(id);
        return Result.ok();
    }

    /** 客户已出库未开票清单（可选客户过滤） */
    @Operation(summary = "客户待开票清单")
    @RequirePerm("invoice:sales:list")
    @GetMapping("/toBeInvoiced")
    public Result<List<ToBeInvoicedVO>> toBeInvoiced(@RequestParam(required = false) Long customerId) {
        return Result.ok(salesInvoiceService.toBeInvoiced(customerId));
    }
}
