package com.erp.qc.controller;

import com.erp.common.result.Result;
import com.erp.qc.dto.vo.QcStatisticsVO;
import com.erp.qc.service.QcInspectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import com.erp.framework.security.RequirePerm;

/**
 * 质量统计接口：按物料/供应商的合格率与检验单量趋势。
 */
@Tag(name = "质量管理-统计")
@RestController
@RequestMapping("/qc/statistics")
@RequiredArgsConstructor
public class QcStatisticsController {

    private final QcInspectService inspectService;

    /** 质量统计（仅已检验单；from/to 缺省为最近 30 天） */
    @Operation(summary = "质量统计（合格率+趋势）")
    @RequirePerm("qc:inspect:list")
    @GetMapping
    public Result<QcStatisticsVO> statistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(inspectService.statistics(from, to));
    }
}
