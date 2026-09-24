package com.mes.quality.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.quality.dto.form.QcInspectForm;
import com.mes.quality.dto.form.QcInspectItemFillForm;
import com.mes.quality.dto.vo.QcInspectVO;
import com.mes.quality.service.QcInspectService;
import com.mes.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 检验单接口（IQC/IPQC/FQC 共用）。
 */
@Tag(name = "质量-检验单")
@RestController
@RequestMapping("/quality/inspects")
@RequiredArgsConstructor
public class QcInspectController {

    private final QcInspectService inspectService;

    /**
     * 创建检验单（按模板带出项目行）。
     */
    @Operation(summary = "创建检验单")
    @RequirePerm("quality:inspect:add")
    @OperLog(module = "检验单", operation = "创建检验单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody QcInspectForm form) {
        return Result.ok(inspectService.create(form));
    }

    /**
     * 录入项目实测值。
     */
    @Operation(summary = "录入检验实测值")
    @RequirePerm("quality:inspect:fill")
    @OperLog(module = "检验单", operation = "录入检验实测值")
    @PutMapping("/{id}/values")
    public Result<Void> fillValues(@PathVariable Long id,
                                   @Valid @RequestBody List<QcInspectItemFillForm> fills) {
        inspectService.fillValues(id, fills);
        return Result.ok();
    }

    /**
     * 提交检验单（汇总结论并回传 ERP）。
     */
    @Operation(summary = "提交检验单")
    @RequirePerm("quality:inspect:submit")
    @OperLog(module = "检验单", operation = "提交检验单")
    @Idempotent
    @PutMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        inspectService.submit(id);
        return Result.ok();
    }

    /**
     * 分页查询。
     */
    @Operation(summary = "分页查询检验单")
    @RequirePerm("quality:inspect:list")
    @GetMapping
    public Result<PageResult<QcInspectVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                @RequestParam(defaultValue = "10") long pageSize,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String inspectType,
                                                @RequestParam(required = false) Integer status) {
        return Result.ok(inspectService.page(pageNum, pageSize, keyword, inspectType, status));
    }

    /**
     * 详情（含项目行）。
     */
    @Operation(summary = "检验单详情")
    @RequirePerm("quality:inspect:list")
    @GetMapping("/{id}")
    public Result<QcInspectVO> detail(@PathVariable Long id) {
        return Result.ok(inspectService.detail(id));
    }
}
