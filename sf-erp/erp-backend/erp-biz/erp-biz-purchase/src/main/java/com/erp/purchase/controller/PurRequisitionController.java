package com.erp.purchase.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.purchase.dto.form.PurRequisitionForm;
import com.erp.purchase.dto.query.PurRequisitionQuery;
import com.erp.purchase.dto.vo.OptionVO;
import com.erp.purchase.dto.vo.PurRequisitionVO;
import com.erp.purchase.mapper.PurRequisitionMapper;
import com.erp.purchase.service.PurRequisitionService;
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

import java.util.List;

/**
 * 请购单接口（请购 -> 询比价 -> 采购订单链路的源头单据）。
 * Controller 只做参数接收/校验/组装响应，业务逻辑一律在 Service。
 */
@Tag(name = "采购-请购单")
@RestController
@RequestMapping("/purchase/requisitions")
@RequiredArgsConstructor
public class PurRequisitionController {

    private final PurRequisitionService requisitionService;
    private final PurRequisitionMapper requisitionMapper;

    /** 分页查询请购单（单号模糊/状态/部门/物料筛选） */
    @Operation(summary = "分页查询请购单")
    @RequirePerm("purchase:requisition:list")
    @GetMapping
    public Result<PageResult<PurRequisitionVO>> page(PurRequisitionQuery query) {
        return Result.ok(requisitionService.page(query));
    }

    /** 请购单详情（主表+明细，明细含已转采购数量） */
    @Operation(summary = "请购单详情")
    @RequirePerm("purchase:requisition:list")
    @GetMapping("/{id}")
    public Result<PurRequisitionVO> detail(@PathVariable Long id) {
        return Result.ok(requisitionService.detail(id));
    }

    /** 请购部门下拉选项（登录即可访问，口径同 /purchase/options/*） */
    @Operation(summary = "请购部门选项")
    @GetMapping("/dept-options")
    public Result<List<OptionVO>> deptOptions() {
        return Result.ok(requisitionMapper.listDeptOptions());
    }

    /** 新增请购单（草稿） */
    @Operation(summary = "新增请购单")
    @RequirePerm("purchase:requisition:add")
    @OperLog(module = "采购管理", operation = "新增请购单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PurRequisitionForm form) {
        return Result.ok(requisitionService.create(form));
    }

    /** 修改请购单（仅草稿） */
    @Operation(summary = "修改请购单")
    @RequirePerm("purchase:requisition:edit")
    @OperLog(module = "采购管理", operation = "修改请购单")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PurRequisitionForm form) {
        requisitionService.update(id, form);
        return Result.ok();
    }

    /** 提交请购单（草稿 -> 已提交） */
    @Operation(summary = "提交请购单")
    @RequirePerm("purchase:requisition:audit")
    @OperLog(module = "采购管理", operation = "提交请购单")
    @Idempotent
    @PutMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        requisitionService.submit(id);
        return Result.ok();
    }

    /** 关闭请购单（已提交/已转采购 -> 已关闭，余量不再采购） */
    @Operation(summary = "关闭请购单")
    @RequirePerm("purchase:requisition:close")
    @OperLog(module = "采购管理", operation = "关闭请购单")
    @Idempotent
    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        requisitionService.close(id);
        return Result.ok();
    }

    /** 删除请购单（仅草稿，逻辑删除） */
    @Operation(summary = "删除请购单")
    @RequirePerm("purchase:requisition:delete")
    @OperLog(module = "采购管理", operation = "删除请购单")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requisitionService.delete(id);
        return Result.ok();
    }
}
