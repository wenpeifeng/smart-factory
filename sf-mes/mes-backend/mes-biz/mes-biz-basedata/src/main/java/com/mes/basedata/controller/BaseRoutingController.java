package com.mes.basedata.controller;

import com.mes.basedata.dto.vo.BaseRoutingVO;
import com.mes.basedata.entity.BaseRouting;
import com.mes.basedata.service.BaseRoutingService;
import com.mes.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工艺路线查询接口（工单创建时回显；维护页面随主数据完善补齐）。
 */
@Tag(name = "基础资料-工艺路线")
@RestController
@RequestMapping("/basedata/routings")
@RequiredArgsConstructor
public class BaseRoutingController {

    private final BaseRoutingService routingService;

    /**
     * 查询物料的有效工艺路线。
     */
    @Operation(summary = "物料有效工艺路线")
    @GetMapping("/active")
    public Result<BaseRoutingVO> active(@RequestParam Long materialId) {
        BaseRouting routing = routingService.findActive(materialId);
        return Result.ok(routing == null ? null : BaseRoutingVO.from(routing));
    }
}
