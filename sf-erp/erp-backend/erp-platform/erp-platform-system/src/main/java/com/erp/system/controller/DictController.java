package com.erp.system.controller;

import com.erp.common.result.Result;
import com.erp.system.dto.dict.DictItemVO;
import com.erp.system.service.SysDictDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开字典接口 —— 供所有业务模块拉取下拉选项。
 * <p>
 * 路径 {@code GET /dict/{type}}（type 为字典类型编码，如 sys_common_status），
 * 已加入 SecurityConfig 白名单；仅返回启用状态的 label/value 选项。
 */
@Tag(name = "公共字典")
@RestController
@RequestMapping("/dict")
@RequiredArgsConstructor
public class DictController {

    private final SysDictDataService dictDataService;

    /** 按类型编码取字典选项 */
    @Operation(summary = "按类型取字典选项")
    @GetMapping("/{type}")
    public Result<List<DictItemVO>> listByType(@PathVariable String type) {
        return Result.ok(dictDataService.listEnabledByType(type));
    }
}
