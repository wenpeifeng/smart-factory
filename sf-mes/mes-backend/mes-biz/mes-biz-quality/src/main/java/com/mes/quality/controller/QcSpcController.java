package com.mes.quality.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.quality.dto.form.QcSpcRuleForm;
import com.mes.quality.dto.vo.QcSpcChartVO;
import com.mes.quality.dto.vo.QcSpcRuleVO;
import com.mes.quality.service.QcSpcService;
import com.mes.system.annotation.OperLog;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SPC 控制图接口（规则维护 + 图表数据）。
 */
@Tag(name = "质量-SPC控制图")
@RestController
@RequestMapping("/quality/spc")
@RequiredArgsConstructor
public class QcSpcController {

    private final QcSpcService spcService;

    /**
     * 分页查询规则。
     */
    @Operation(summary = "分页查询SPC规则")
    @RequirePerm("quality:spc:list")
    @GetMapping("/rules")
    public Result<PageResult<QcSpcRuleVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                @RequestParam(defaultValue = "10") long pageSize,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Long materialId) {
        return Result.ok(spcService.page(pageNum, pageSize, keyword, materialId));
    }

    /**
     * 创建/修改规则（物料+项目唯一，存在即更新）。
     */
    @Operation(summary = "保存SPC规则")
    @RequirePerm("quality:spc:edit")
    @OperLog(module = "SPC", operation = "保存SPC规则")
    @Idempotent
    @PostMapping("/rules")
    public Result<Long> save(@Valid @RequestBody QcSpcRuleForm form) {
        return Result.ok(spcService.save(form));
    }

    /**
     * 删除规则。
     */
    @Operation(summary = "删除SPC规则")
    @RequirePerm("quality:spc:edit")
    @OperLog(module = "SPC", operation = "删除SPC规则")
    @DeleteMapping("/rules/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        spcService.delete(id);
        return Result.ok();
    }

    /**
     * 启停规则。
     */
    @Operation(summary = "启停SPC规则")
    @RequirePerm("quality:spc:edit")
    @OperLog(module = "SPC", operation = "启停SPC规则")
    @PutMapping("/rules/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        spcService.changeStatus(id, status);
        return Result.ok();
    }

    /**
     * 控制图数据（规则 + 中心线 + 最近样本）。
     */
    @Operation(summary = "SPC控制图数据")
    @RequirePerm("quality:spc:list")
    @GetMapping("/rules/{id}/chart")
    public Result<QcSpcChartVO> chart(@PathVariable Long id,
                                      @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(spcService.chart(id, limit));
    }
}
