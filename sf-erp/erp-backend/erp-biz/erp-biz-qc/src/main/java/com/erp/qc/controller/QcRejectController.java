package com.erp.qc.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.qc.dto.form.QcRejectForm;
import com.erp.qc.dto.query.QcRejectQuery;
import com.erp.qc.dto.vo.QcRejectVO;
import com.erp.qc.dto.vo.RejectSourceVO;
import com.erp.qc.service.QcRejectService;
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
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 不合格品处置接口（从已检验且不合格数>0 的检验单生成处置决定）。
 */
@Tag(name = "质量管理-不合格品处置")
@RestController
@RequestMapping("/qc/rejects")
@RequiredArgsConstructor
public class QcRejectController {

    private final QcRejectService rejectService;

    /** 分页查询处置单 */
    @Operation(summary = "分页查询不合格品处置单")
    @RequirePerm("qc:reject:list")
    @GetMapping
    public Result<PageResult<QcRejectVO>> page(QcRejectQuery query) {
        return Result.ok(rejectService.page(query));
    }

    /** 处置单详情 */
    @Operation(summary = "不合格品处置单详情")
    @RequirePerm("qc:reject:list")
    @GetMapping("/{id}")
    public Result<QcRejectVO> detail(@PathVariable Long id) {
        return Result.ok(rejectService.getDetail(id));
    }

    /** 可生成处置单的检验单选项（不合格数-已处置 仍有余额） */
    @Operation(summary = "可处置检验单选项")
    @GetMapping("/sources")
    public Result<List<RejectSourceVO>> sources() {
        return Result.ok(rejectService.listSources());
    }

    /** 新增处置单 */
    @Operation(summary = "新增不合格品处置单")
    @RequirePerm("qc:reject:add")
    @OperLog(module = "质量管理", operation = "新增不良品单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody QcRejectForm form) {
        return Result.ok(rejectService.create(form));
    }

    /** 修改处置单 */
    @Operation(summary = "修改不合格品处置单")
    @RequirePerm("qc:reject:edit")
    @OperLog(module = "质量管理", operation = "修改不良品单")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody QcRejectForm form) {
        rejectService.update(id, form);
        return Result.ok();
    }

    /** 删除处置单 */
    @Operation(summary = "删除不合格品处置单")
    @RequirePerm("qc:reject:delete")
    @OperLog(module = "质量管理", operation = "删除不良品单")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        rejectService.delete(id);
        return Result.ok();
    }
}
