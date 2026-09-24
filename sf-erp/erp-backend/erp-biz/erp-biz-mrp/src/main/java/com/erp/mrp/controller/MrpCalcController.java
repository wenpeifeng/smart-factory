package com.erp.mrp.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.mrp.dto.form.MrpRunForm;
import com.erp.mrp.dto.vo.BatchDetailVO;
import com.erp.mrp.dto.vo.CalcBatchVO;
import com.erp.mrp.dto.vo.MrpRunResultVO;
import com.erp.mrp.service.MrpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * MRP 运算接口：触发运算、批次分页、按批次查看全树展开明细。
 */
@Tag(name = "生产制造-MRP运算")
@RestController
@RequestMapping("/mrp/calc")
@RequiredArgsConstructor
public class MrpCalcController {

    private final MrpService mrpService;

    /** 触发 MRP 运算（mpsIds 空=全部待运算） */
    @Operation(summary = "执行MRP运算")
    @RequirePerm("mrp:mrp:audit")
    @OperLog(module = "生产制造", operation = "执行MRP运算")
    @Idempotent
    @PostMapping("/run")
    public Result<MrpRunResultVO> run(@RequestBody(required = false) MrpRunForm form) {
        return Result.ok(mrpService.run(form == null ? new MrpRunForm() : form));
    }

    /** 运算批次分页 */
    @Operation(summary = "运算批次分页")
    @RequirePerm("mrp:mrp:list")
    @GetMapping("/batches")
    public Result<PageResult<CalcBatchVO>> pageBatches(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(mrpService.pageBatches(pageNum, pageSize));
    }

    /** 按批次查看全树展开明细 */
    @Operation(summary = "批次全树展开明细")
    @RequirePerm("mrp:mrp:list")
    @GetMapping("/batches/{batchNo}/details")
    public Result<BatchDetailVO> batchDetails(@PathVariable String batchNo) {
        return Result.ok(mrpService.batchDetails(batchNo));
    }
}
