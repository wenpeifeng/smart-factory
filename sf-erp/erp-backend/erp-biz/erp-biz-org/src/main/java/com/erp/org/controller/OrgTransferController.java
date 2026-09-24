package com.erp.org.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.org.dto.form.TransferForm;
import com.erp.org.dto.request.TransferQuery;
import com.erp.org.dto.vo.TransferVO;
import com.erp.org.service.OrgTransferService;
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

import java.util.List;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 组织间调拨单接口（工厂间实物调拨：草稿 → 审核过账 / 取消）。
 * <p>
 * 审核在事务内完成「调出仓扣库存 + 调入仓加库存 + inv_movement 两行
 * （ORG_OUT/ORG_IN）」，库存不足整体回滚。
 */
@Tag(name = "组织管理-组织间调拨")
@RestController
@RequestMapping("/org/transfers")
@RequiredArgsConstructor
public class OrgTransferController {

    private final OrgTransferService transferService;

    /** 调拨单分页（状态/线路/关键词/创建日期区间） */
    @Operation(summary = "分页查询调拨单")
    @RequirePerm("org:transfer:list")
    @GetMapping
    public Result<PageResult<TransferVO>> page(@ModelAttribute TransferQuery query) {
        return Result.ok(transferService.page(query));
    }

    /** 调拨单详情（头 + 明细行） */
    @Operation(summary = "调拨单详情")
    @RequirePerm("org:transfer:list")
    @GetMapping("/{id}")
    public Result<TransferVO.Detail> detail(@PathVariable Long id) {
        return Result.ok(transferService.detail(id));
    }

    /** 明细价格预演（保存前实时展示成本/结算价列，不落库） */
    @Operation(summary = "调拨明细价格预演")
    @RequirePerm("org:transfer:list")
    @PostMapping("/preview")
    public Result<List<TransferVO.LineVO>> preview(@Valid @RequestBody TransferForm form) {
        return Result.ok(transferService.preview(form));
    }

    /** 新增调拨单（草稿；明细行自动按价目算结算价） */
    @Operation(summary = "新增调拨单")
    @RequirePerm("org:transfer:add")
    @OperLog(module = "组织间结算", operation = "新增转移单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody TransferForm form) {
        return Result.ok(transferService.create(form));
    }

    /** 修改调拨单（仅草稿；明细整体重算重存） */
    @Operation(summary = "修改调拨单")
    @RequirePerm("org:transfer:edit")
    @OperLog(module = "组织间结算", operation = "修改转移单")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TransferForm form) {
        transferService.update(id, form);
        return Result.ok();
    }

    /** 审核过账（事务：扣/加库存 + ORG_OUT/ORG_IN 流水；库存不足回滚） */
    @Operation(summary = "审核调拨单")
    @RequirePerm("org:transfer:audit")
    @OperLog(module = "组织间结算", operation = "审核转移单")
    @Idempotent
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id) {
        transferService.audit(id);
        return Result.ok();
    }

    /** 取消（仅草稿） */
    @Operation(summary = "取消调拨单")
    @RequirePerm("org:transfer:audit")
    @OperLog(module = "组织间结算", operation = "取消转移单")
    @Idempotent
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        transferService.cancel(id);
        return Result.ok();
    }
}
