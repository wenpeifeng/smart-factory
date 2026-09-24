package com.erp.org.controller;

import com.erp.common.result.Result;
import com.erp.org.dto.vo.FactoryStockVO;
import com.erp.org.dto.vo.TransferLineStatVO;
import com.erp.org.service.OrgFactoryService;
import com.erp.org.service.OrgTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import com.erp.framework.security.RequirePerm;

/**
 * 组织管理统计接口（只读聚合：工厂库存 / 调拨线路金额）。
 */
@Tag(name = "组织管理-统计")
@RestController
@RequestMapping("/org/statistics")
@RequiredArgsConstructor
public class OrgStatisticsController {

    private final OrgFactoryService factoryService;
    private final OrgTransferService transferService;

    /** 工厂下各仓库库存量汇总（JOIN inv_stock × base_warehouse 只读） */
    @Operation(summary = "工厂库存统计")
    @RequirePerm("org:statistics:list")
    @GetMapping("/stock")
    public Result<List<FactoryStockVO>> stock(
            @Parameter(description = "工厂 ID（必填）") @RequestParam Long factoryId) {
        return Result.ok(factoryService.statisticsStock(factoryId));
    }

    /** 期间调拨金额按线路汇总（已审核单；from/to 缺省为本月 1 号~今天） */
    @Operation(summary = "调拨线路统计")
    @RequirePerm("org:statistics:list")
    @GetMapping("/transfer")
    public Result<List<TransferLineStatVO>> transfer(
            @Parameter(description = "开始日期 yyyy-MM-dd")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "结束日期 yyyy-MM-dd")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(transferService.statisticsLine(from, to));
    }
}
