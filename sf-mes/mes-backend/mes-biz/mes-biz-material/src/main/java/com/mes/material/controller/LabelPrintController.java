package com.mes.material.controller;

import com.mes.common.result.Result;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.material.dto.form.LabelPrintForm;
import com.mes.material.service.LabelPrintService;
import com.mes.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 标签打印接口。
 */
@Tag(name = "物料-标签打印")
@RestController
@RequestMapping("/material/labels")
@RequiredArgsConstructor
public class LabelPrintController {

    private final LabelPrintService labelPrintService;

    /**
     * 登记一次标签打印（前端渲染模板并打印，此处留痕）。
     */
    @Operation(summary = "标签打印登记")
    @RequirePerm("material:label:print")
    @OperLog(module = "标签打印", operation = "标签打印")
    @Idempotent
    @PostMapping
    public Result<Long> print(@Valid @RequestBody LabelPrintForm form) {
        return Result.ok(labelPrintService.record(form));
    }
}
