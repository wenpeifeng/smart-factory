package com.erp.purchase.controller;

import com.erp.common.result.Result;
import com.erp.purchase.dto.vo.OptionVO;
import com.erp.purchase.mapper.BasedataMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采购单据填写辅助接口：基础资料下拉选项（供应商/物料/仓库）。
 * <p>
 * basedata 模块标准接口由他人并行开发；为保证采购单据可用性，
 * 此处通过本模块只读 Mapper 直查 base_* 标准表提供轻量选项。
 * 联调后前端可切换到 basedata 标准接口（数据口径一致，见 docs/modules/purchase.md）。
 */
@Tag(name = "采购-基础资料选项")
@RestController
@RequestMapping("/purchase/options")
@RequiredArgsConstructor
public class PurchaseOptionsController {

    private final BasedataMapper basedataMapper;

    /** 供应商下拉选项 */
    @Operation(summary = "供应商选项")
    @GetMapping("/suppliers")
    public Result<List<OptionVO>> suppliers() {
        return Result.ok(basedataMapper.listSuppliers());
    }

    /** 物料下拉选项（含规格/计量单位） */
    @Operation(summary = "物料选项")
    @GetMapping("/materials")
    public Result<List<OptionVO>> materials() {
        return Result.ok(basedataMapper.listMaterials());
    }

    /** 仓库下拉选项 */
    @Operation(summary = "仓库选项")
    @GetMapping("/warehouses")
    public Result<List<OptionVO>> warehouses() {
        return Result.ok(basedataMapper.listWarehouses());
    }
}
