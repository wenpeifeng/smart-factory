package com.mes.quality.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.quality.dto.form.QcRejectForm;
import com.mes.quality.dto.vo.QcRejectVO;
import com.mes.quality.service.QcRejectService;
import com.mes.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 不合格品处置接口。
 */
@Tag(name = "质量-不合格处置")
@RestController
@RequestMapping("/quality/rejects")
@RequiredArgsConstructor
public class QcRejectController {

    private final QcRejectService rejectService;

    /**
     * 创建处置单。
     */
    @Operation(summary = "创建不合格处置单")
    @RequirePerm("quality:reject:add")
    @OperLog(module = "不合格处置", operation = "创建不合格处置单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody QcRejectForm form) {
        return Result.ok(rejectService.create(form));
    }

    /**
     * 分页查询。
     */
    @Operation(summary = "分页查询处置单")
    @RequirePerm("quality:reject:list")
    @GetMapping
    public Result<PageResult<QcRejectVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               @RequestParam(required = false) String keyword) {
        return Result.ok(rejectService.page(pageNum, pageSize, keyword));
    }
}
