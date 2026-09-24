package com.erp.purchase.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.purchase.dto.form.PurRfqForm;
import com.erp.purchase.dto.form.RfqAwardForm;
import com.erp.purchase.dto.form.RfqQuoteForm;
import com.erp.purchase.dto.query.PurRfqQuery;
import com.erp.purchase.dto.vo.PurRfqVO;
import com.erp.purchase.dto.vo.RfqComparisonVO;
import com.erp.purchase.dto.vo.RfqToOrderVO;
import com.erp.purchase.service.PurRfqService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 询比价单接口（请购 -> 询比价 -> 采购订单链路的中间单据）。
 * 比价矩阵 / 分项定标 / 下推采购订单是本控制器的三个核心动作。
 */
@Tag(name = "采购-询比价单")
@RestController
@RequestMapping("/purchase/rfqs")
@RequiredArgsConstructor
public class PurRfqController {

    private final PurRfqService rfqService;

    /** 分页查询询比价单（单号模糊/状态/来源请购单/供应商筛选） */
    @Operation(summary = "分页查询询比价单")
    @RequirePerm("purchase:rfq:list")
    @GetMapping
    public Result<PageResult<PurRfqVO>> page(PurRfqQuery query) {
        return Result.ok(rfqService.page(query));
    }

    /** 询比价单详情（主表+邀请供应商+明细含定标/下推回写） */
    @Operation(summary = "询比价单详情")
    @RequirePerm("purchase:rfq:list")
    @GetMapping("/{id}")
    public Result<PurRfqVO> detail(@PathVariable Long id) {
        return Result.ok(rfqService.detail(id));
    }

    /** 比价矩阵：物料×供应商（单价/交期/总价）+ 每物料最低价标记 */
    @Operation(summary = "询比价比价矩阵")
    @RequirePerm("purchase:rfq:list")
    @GetMapping("/{id}/comparison")
    public Result<RfqComparisonVO> comparison(@PathVariable Long id) {
        return Result.ok(rfqService.comparison(id));
    }

    /** 新增询比价单（询价中，可关联请购单带入明细） */
    @Operation(summary = "新增询比价单")
    @RequirePerm("purchase:rfq:add")
    @OperLog(module = "采购管理", operation = "新增询比价单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PurRfqForm form) {
        return Result.ok(rfqService.create(form));
    }

    /** 修改询比价单（仅询价中：截止日期/备注/邀请供应商） */
    @Operation(summary = "修改询比价单")
    @RequirePerm("purchase:rfq:edit")
    @OperLog(module = "采购管理", operation = "修改询比价单")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PurRfqForm form) {
        rfqService.update(id, form);
        return Result.ok();
    }

    /** 录入供应商报价（仅询价中，同供应商重录=覆盖） */
    @Operation(summary = "录入供应商报价")
    @RequirePerm("purchase:rfq:edit")
    @OperLog(module = "采购管理", operation = "录入询比价报价")
    @Idempotent
    @PostMapping("/{id}/quotes")
    public Result<Void> saveQuotes(@PathVariable Long id, @Valid @RequestBody RfqQuoteForm form) {
        rfqService.saveQuotes(id, form);
        return Result.ok();
    }

    /** 定标（支持分项定标：每明细选一个已报价供应商） */
    @Operation(summary = "询比价定标")
    @RequirePerm("purchase:rfq:award")
    @OperLog(module = "采购管理", operation = "询比价定标")
    @Idempotent
    @PostMapping("/{id}/award")
    public Result<Void> award(@PathVariable Long id, @Valid @RequestBody RfqAwardForm form) {
        rfqService.award(id, form);
        return Result.ok();
    }

    /** 下推采购订单草稿（按定标供应商分组，一供应商一单） */
    @Operation(summary = "下推采购订单")
    @RequirePerm("purchase:rfq:toOrder")
    @OperLog(module = "采购管理", operation = "询比价下推采购订单")
    @Idempotent
    @PostMapping("/{id}/to-orders")
    public Result<RfqToOrderVO> toOrders(@PathVariable Long id) {
        return Result.ok(rfqService.toOrders(id));
    }

    /** 取消询比价单（仅询价中 -> 已取消） */
    @Operation(summary = "取消询比价单")
    @RequirePerm("purchase:rfq:edit")
    @OperLog(module = "采购管理", operation = "取消询比价单")
    @Idempotent
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        rfqService.cancel(id);
        return Result.ok();
    }

    /** 删除询比价单（仅询价中，逻辑删除） */
    @Operation(summary = "删除询比价单")
    @RequirePerm("purchase:rfq:delete")
    @OperLog(module = "采购管理", operation = "删除询比价单")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        rfqService.delete(id);
        return Result.ok();
    }
}
