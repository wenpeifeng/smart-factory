package com.mes.material.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.framework.security.RequirePerm;
import com.mes.material.dto.vo.ShopStockVO;
import com.mes.material.service.ShopStockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 线边仓库存接口。
 */
@Tag(name = "物料-线边仓")
@RestController
@RequestMapping("/material/shop-stocks")
@RequiredArgsConstructor
public class ShopStockController {

    private final ShopStockService shopStockService;

    /**
     * 分页查询线边仓库存。
     */
    @Operation(summary = "分页查询线边仓库存")
    @RequirePerm("material:shopstock:list")
    @GetMapping
    public Result<PageResult<ShopStockVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                @RequestParam(defaultValue = "10") long pageSize,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Long workcenterId) {
        return Result.ok(shopStockService.page(pageNum, pageSize, keyword, workcenterId));
    }
}
