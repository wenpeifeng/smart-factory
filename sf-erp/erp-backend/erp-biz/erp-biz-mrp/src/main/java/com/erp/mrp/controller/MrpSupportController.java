package com.erp.mrp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.mrp.dto.vo.MaterialBriefVO;
import com.erp.mrp.dto.vo.SupplierBriefVO;
import com.erp.mrp.dto.vo.WarehouseBriefVO;
import com.erp.mrp.mapper.MrpIntegrationMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 生产制造支撑接口 —— 为本模块前端页面提供物料/仓库/供应商选择器数据
 * （跨模块只读投影，走约定表，不依赖对方 Java 代码）。
 */
@Tag(name = "生产制造-支撑数据")
@RestController
@RequestMapping("/mrp")
@RequiredArgsConstructor
public class MrpSupportController {

    private final MrpIntegrationMapper integrationMapper;

    /** 物料分页（选择器） */
    @Operation(summary = "物料选择器数据")
    @GetMapping("/materials")
    public Result<PageResult<MaterialBriefVO>> materials(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword) {
        IPage<MaterialBriefVO> page = integrationMapper.pageMaterials(new Page<>(pageNum, pageSize), keyword);
        return Result.ok(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 仓库列表（选择器） */
    @Operation(summary = "仓库选择器数据")
    @GetMapping("/warehouses")
    public Result<List<WarehouseBriefVO>> warehouses() {
        return Result.ok(integrationMapper.selectWarehouses());
    }

    /** 供应商列表（选择器） */
    @Operation(summary = "供应商选择器数据")
    @GetMapping("/suppliers")
    public Result<List<SupplierBriefVO>> suppliers(@RequestParam(required = false) String keyword) {
        return Result.ok(integrationMapper.selectSuppliers(keyword));
    }
}
