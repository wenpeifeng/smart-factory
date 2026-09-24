package com.erp.purchase.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.purchase.dto.form.EstimationReverseForm;
import com.erp.purchase.dto.query.PurEstimationQuery;
import com.erp.purchase.dto.vo.PurEstimationSummaryVO;
import com.erp.purchase.dto.vo.PurEstimationVO;
import com.erp.purchase.service.PurEstimationService;
import com.erp.system.annotation.OperLog;
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

/**
 * 采购暂估应付（货到票未到）接口——V231。
 * <p>
 * 权限码：purchase:estimation:list（查询/报表）、
 * purchase:estimation:edit（手工生成暂估）、purchase:estimation:audit（手动冲回）。
 */
@Tag(name = "采购暂估应付")
@RestController
@RequestMapping("/purchase/estimations")
@RequiredArgsConstructor
public class PurEstimationController {

    private final PurEstimationService purEstimationService;

    /** 分页查询暂估单（keyword 匹配暂估单号/入库单号/发票号） */
    @Operation(summary = "分页查询暂估单")
    @RequirePerm("purchase:estimation:list")
    @GetMapping
    public Result<PageResult<PurEstimationVO>> page(PurEstimationQuery query) {
        return Result.ok(purEstimationService.page(query));
    }

    /** 暂估报表：当月暂估总额/已冲回/未冲回余额（供应商维度），month=yyyyMM 缺省当月 */
    @Operation(summary = "暂估报表（按期间汇总）")
    @RequirePerm("purchase:estimation:list")
    @GetMapping("/summary")
    public Result<PurEstimationSummaryVO> summary(@RequestParam(required = false) String month) {
        return Result.ok(purEstimationService.summary(month));
    }

    /** 手工生成暂估（入库单已审核且无生效发票；自动暂估漏单的补救入口） */
    @Operation(summary = "按入库单手工生成暂估")
    @RequirePerm("purchase:estimation:edit")
    @OperLog(module = "采购管理", operation = "手工生成暂估应付")
    @Idempotent
    @PostMapping("/from-instock/{instockId}")
    public Result<Long> createFromInstock(@PathVariable Long instockId) {
        return Result.ok(purEstimationService.createFromInstock(instockId));
    }

    /** 手动冲回（发票实际金额=价税合计；自动冲回漏单的补救入口） */
    @Operation(summary = "暂估手动冲回")
    @RequirePerm("purchase:estimation:audit")
    @OperLog(module = "采购管理", operation = "暂估应付冲回")
    @Idempotent
    @PostMapping("/{id}/reverse")
    public Result<Void> reverse(@PathVariable Long id, @Valid @RequestBody EstimationReverseForm form) {
        purEstimationService.reverse(id, form);
        return Result.ok();
    }
}
