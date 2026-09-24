package com.mes.basedata.controller;

import com.mes.basedata.dto.vo.BaseBomVO;
import com.mes.basedata.entity.BaseBom;
import com.mes.basedata.service.BaseBomService;
import com.mes.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * BOM 查询接口（工单创建时选择版本；维护页面随主数据完善补齐）。
 */
@Tag(name = "基础资料-BOM")
@RestController
@RequestMapping("/basedata/boms")
@RequiredArgsConstructor
public class BaseBomController {

    private final BaseBomService bomService;

    /**
     * 查询物料的 BOM 版本列表。
     */
    @Operation(summary = "物料BOM版本列表")
    @GetMapping
    public Result<List<BaseBomVO>> listByMaterial(@RequestParam Long materialId) {
        return Result.ok(bomService.listByMaterial(materialId).stream().map(BaseBomVO::from).toList());
    }
}
