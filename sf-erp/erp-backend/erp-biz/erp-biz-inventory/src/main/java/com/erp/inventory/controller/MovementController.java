package com.erp.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.inventory.dto.request.MovementQuery;
import com.erp.inventory.dto.vo.MovementVO;
import com.erp.inventory.mapper.InvMovementMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.framework.security.RequirePerm;

/**
 * 库存流水查询接口（只读；写入一律走 StockService）。
 */
@Tag(name = "库存管理-库存流水")
@RestController
@RequestMapping("/inventory/movements")
@RequiredArgsConstructor
public class MovementController {

    private final InvMovementMapper movementMapper;

    /** 流水分页（日期/类型/物料/仓库/单号过滤） */
    @Operation(summary = "库存流水分页查询")
    @RequirePerm("inventory:move:list")
    @GetMapping
    public Result<PageResult<MovementVO>> page(MovementQuery query) {
        IPage<MovementVO> page = movementMapper.selectMovementPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return Result.ok(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }
}
