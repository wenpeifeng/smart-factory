package com.mes.basedata.controller;

import com.mes.basedata.dto.vo.BaseWorkcenterVO;
import com.mes.basedata.service.BaseWorkcenterService;
import com.mes.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作中心接口（下拉选择，暂无维护页面；台账维护随 P3 equipment 模块补齐）。
 */
@Tag(name = "基础资料-工作中心")
@RestController
@RequestMapping("/basedata/workcenters")
@RequiredArgsConstructor
public class BaseWorkcenterController {

    private final BaseWorkcenterService workcenterService;

    /**
     * 启用工作中心下拉列表。
     */
    @Operation(summary = "启用工作中心列表")
    @GetMapping("/enabled")
    public Result<List<BaseWorkcenterVO>> enabled() {
        return Result.ok(workcenterService.listEnabled());
    }
}
