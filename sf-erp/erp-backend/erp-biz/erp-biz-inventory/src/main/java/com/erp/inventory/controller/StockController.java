package com.erp.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.inventory.dto.request.StockQuery;
import com.erp.inventory.dto.vo.StockVO;
import com.erp.inventory.dto.vo.WarningVO;
import com.erp.inventory.mapper.InvStockMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.framework.security.RequirePerm;

/**
 * 即时库存接口：库存查询 + 安全库存预警。
 */
@Tag(name = "库存管理-即时库存")
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class StockController {

    private final InvStockMapper stockMapper;

    /** 即时库存分页（仓库/物料关键词/零库存/仅预警过滤） */
    @Operation(summary = "即时库存分页查询")
    @RequirePerm("inventory:stock:list")
    @GetMapping("/stocks")
    public Result<PageResult<StockVO>> page(StockQuery query) {
        IPage<StockVO> page = stockMapper.selectStockPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return Result.ok(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 低于安全库存的清单（物料为主，无库存按 0 计） */
    @Operation(summary = "安全库存预警清单")
    @RequirePerm("inventory:stock:list")
    @GetMapping("/warnings")
    public Result<PageResult<WarningVO>> warnings(StockQuery query) {
        IPage<WarningVO> page = stockMapper.selectWarningPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return Result.ok(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }
}
