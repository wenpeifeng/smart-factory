package com.erp.invoice.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.invoice.dto.form.PurchaseInvoiceRegisterForm;
import com.erp.invoice.dto.request.PurchaseInvoiceQuery;
import com.erp.invoice.dto.vo.PurchaseInvoiceVO;
import com.erp.invoice.dto.vo.ToBeRegisteredVO;
import com.erp.invoice.service.InvPurchaseInvoiceService;
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
 * 进项发票接口：分页查询、登记（从采购入库单）、认证、红冲、供应商待登记清单。
 */
@Tag(name = "发票管理-进项发票")
@RestController
@RequestMapping("/invoice/purchases")
@RequiredArgsConstructor
public class InvPurchaseInvoiceController {

    private final InvPurchaseInvoiceService purchaseInvoiceService;

    /** 分页查询进项发票（供应商/状态/关键词/开票日期区间） */
    @Operation(summary = "分页查询进项发票")
    @RequirePerm("invoice:purchase:list")
    @GetMapping
    public Result<PageResult<PurchaseInvoiceVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(purchaseInvoiceService.page(
                new PurchaseInvoiceQuery(supplierId, status, keyword, from, to), pageNum, pageSize));
    }

    /** 发票明细 */
    @Operation(summary = "进项发票明细")
    @RequirePerm("invoice:purchase:list")
    @GetMapping("/{id}")
    public Result<PurchaseInvoiceVO> detail(@PathVariable Long id) {
        return Result.ok(purchaseInvoiceService.detail(id));
    }

    /** 登记：从采购入库单生成待认证发票（税率默认取明细统一税率，否则 13%） */
    @Operation(summary = "登记进项发票（从采购入库单）")
    @RequirePerm("invoice:purchase:add")
    @OperLog(module = "发票管理", operation = "登记采购发票")
    @Idempotent
    @PostMapping("/from-instock/{instockId}")
    public Result<Long> registerFromInstock(@PathVariable Long instockId,
                                            @Valid @RequestBody(required = false) PurchaseInvoiceRegisterForm form) {
        return Result.ok(purchaseInvoiceService.registerFromInstock(instockId, form));
    }

    /** 认证：待认证 → 已认证（已认证税额进入税负统计） */
    @Operation(summary = "认证")
    @RequirePerm("invoice:purchase:audit")
    @OperLog(module = "发票管理", operation = "认证采购发票")
    @Idempotent
    @PostMapping("/{id}/authenticate")
    public Result<Void> authenticate(@PathVariable Long id) {
        purchaseInvoiceService.authenticate(id);
        return Result.ok();
    }

    /** 红冲：原票置已红冲，并生成负数红字发票冲减已认证税额 */
    @Operation(summary = "红冲")
    @RequirePerm("invoice:purchase:audit")
    @OperLog(module = "发票管理", operation = "冲销采购发票")
    @Idempotent
    @PostMapping("/{id}/reverse")
    public Result<Void> reverse(@PathVariable Long id) {
        purchaseInvoiceService.reverse(id);
        return Result.ok();
    }

    /** 供应商已入库未登记发票清单（可选供应商过滤） */
    @Operation(summary = "供应商待登记清单")
    @RequirePerm("invoice:purchase:list")
    @GetMapping("/toBeRegistered")
    public Result<List<ToBeRegisteredVO>> toBeRegistered(@RequestParam(required = false) Long supplierId) {
        return Result.ok(purchaseInvoiceService.toBeRegistered(supplierId));
    }
}
