package com.mes.material.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.material.dto.form.BatchSnForm;
import com.mes.material.dto.vo.BatchSnVO;
import com.mes.material.service.BatchSnService;
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
 * 批次/SN 档案接口。
 */
@Tag(name = "物料-批次SN")
@RestController
@RequestMapping("/material/batch-sns")
@RequiredArgsConstructor
public class BatchSnController {

    private final BatchSnService batchSnService;

    /**
     * 登记批次/SN。
     */
    @Operation(summary = "登记批次/SN档案")
    @RequirePerm("material:batchsn:add")
    @OperLog(module = "批次SN", operation = "登记批次/SN档案")
    @Idempotent
    @PostMapping
    public Result<Long> register(@Valid @RequestBody BatchSnForm form) {
        return Result.ok(batchSnService.register(form));
    }

    /**
     * 分页查询批次/SN 档案。
     */
    @Operation(summary = "分页查询批次/SN")
    @RequirePerm("material:batchsn:list")
    @GetMapping
    public Result<PageResult<BatchSnVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "10") long pageSize,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String objType,
                                              @RequestParam(required = false) Long materialId) {
        return Result.ok(batchSnService.page(pageNum, pageSize, keyword, objType, materialId));
    }
}
