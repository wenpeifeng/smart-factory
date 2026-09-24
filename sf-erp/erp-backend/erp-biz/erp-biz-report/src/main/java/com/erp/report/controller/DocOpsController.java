package com.erp.report.controller;

import com.erp.common.result.Result;
import com.erp.framework.security.RequirePerm;
import com.erp.report.constant.DocTypes;
import com.erp.report.dto.form.ReverseForm;
import com.erp.report.dto.vo.DocChainVO;
import com.erp.report.dto.vo.ReverseResultVO;
import com.erp.report.service.DocChainService;
import com.erp.report.service.ReverseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 单据运营接口（T7）：通用红冲 + 单据联查。
 * <p>
 * 红冲接口按任务约定的 REST 形态挂在各模块路径下
 * （POST /api/{module}/{docType}/{id}/reverse），实现统一收口在 report 模块
 * （依赖 purchase/sales/inventory/finance/cost，天然是跨模块编排层）；
 * 权限码沿用各模块规范（模块:实体:reverse），sys_menu 按钮种子见 V541。
 * <p>
 * 联查：GET /api/basedata/doc-chain?bizType=&amp;bizId=。
 */
@Tag(name = "单据红冲与联查")
@RestController
@RequiredArgsConstructor
public class DocOpsController {

    private final ReverseService reverseService;
    private final DocChainService docChainService;

    // ==================== 通用红冲 ====================

    @Operation(summary = "红冲采购入库单")
    @PostMapping("/purchase/instock/{id}/reverse")
    @RequirePerm("purchase:instock:reverse")
    public Result<ReverseResultVO> reversePurInstock(@PathVariable Long id,
                                                     @RequestBody(required = false) ReverseForm form) {
        return Result.ok(reverseService.reverse(DocTypes.PUR_INSTOCK, id, reasonOf(form)));
    }

    @Operation(summary = "红冲采购退货单")
    @PostMapping("/purchase/return/{id}/reverse")
    @RequirePerm("purchase:return:reverse")
    public Result<ReverseResultVO> reversePurReturn(@PathVariable Long id,
                                                    @RequestBody(required = false) ReverseForm form) {
        return Result.ok(reverseService.reverse(DocTypes.PUR_RETURN, id, reasonOf(form)));
    }

    @Operation(summary = "红冲销售出库单")
    @PostMapping("/sales/outstock/{id}/reverse")
    @RequirePerm("sales:outstock:reverse")
    public Result<ReverseResultVO> reverseSalOutstock(@PathVariable Long id,
                                                      @RequestBody(required = false) ReverseForm form) {
        return Result.ok(reverseService.reverse(DocTypes.SAL_OUTSTOCK, id, reasonOf(form)));
    }

    @Operation(summary = "红冲销售退货单")
    @PostMapping("/sales/return/{id}/reverse")
    @RequirePerm("sales:return:reverse")
    public Result<ReverseResultVO> reverseSalReturn(@PathVariable Long id,
                                                    @RequestBody(required = false) ReverseForm form) {
        return Result.ok(reverseService.reverse(DocTypes.SAL_RETURN, id, reasonOf(form)));
    }

    @Operation(summary = "红冲收款单")
    @PostMapping("/finance/receipt/{id}/reverse")
    @RequirePerm("finance:receipt:reverse")
    public Result<ReverseResultVO> reverseReceipt(@PathVariable Long id,
                                                  @RequestBody(required = false) ReverseForm form) {
        return Result.ok(reverseService.reverse(DocTypes.RECEIPT, id, reasonOf(form)));
    }

    @Operation(summary = "红冲付款单")
    @PostMapping("/finance/payment/{id}/reverse")
    @RequirePerm("finance:payment:reverse")
    public Result<ReverseResultVO> reversePayment(@PathVariable Long id,
                                                  @RequestBody(required = false) ReverseForm form) {
        return Result.ok(reverseService.reverse(DocTypes.PAYMENT, id, reasonOf(form)));
    }

    // ==================== 单据联查 ====================

    @Operation(summary = "单据联查（上下游链路）")
    @GetMapping("/basedata/doc-chain")
    @RequirePerm("basedata:docChain:list")
    public Result<DocChainVO> docChain(@RequestParam String bizType, @RequestParam Long bizId) {
        return Result.ok(docChainService.chain(bizType, bizId));
    }

    private String reasonOf(ReverseForm form) {
        return form == null || form.getReason() == null ? "" : form.getReason().trim();
    }
}
