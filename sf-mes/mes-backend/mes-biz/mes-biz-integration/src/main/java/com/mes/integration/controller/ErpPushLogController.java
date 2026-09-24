package com.mes.integration.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.framework.mybatis.PageUtils;
import com.mes.framework.security.RequirePerm;
import com.mes.integration.dto.PushLogQuery;
import com.mes.integration.dto.PushLogVO;
import com.mes.integration.erp.push.ErpPushLog;
import com.mes.integration.mapper.ErpPushLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ERP 回传推送日志接口（对账/排查）。
 */
@Tag(name = "集成-回传推送日志")
@RestController
@RequestMapping("/integration/push-logs")
@RequiredArgsConstructor
public class ErpPushLogController {

    private final ErpPushLogMapper pushLogMapper;

    /**
     * 分页查询推送日志（对账入口：按状态筛 FAILED 即待补偿清单）。
     */
    @Operation(summary = "分页查询回传推送日志")
    @RequirePerm("integration:pushlog:list")
    @GetMapping
    public Result<PageResult<PushLogVO>> page(PushLogQuery query) {
        var wrapper = Wrappers.lambdaQuery(ErpPushLog.class)
                .eq(StringUtils.hasText(query.getBizType()), ErpPushLog::getBizType, query.getBizType())
                .eq(StringUtils.hasText(query.getStatus()), ErpPushLog::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getBizNo()), ErpPushLog::getBizNo, query.getBizNo())
                .orderByDesc(ErpPushLog::getId);
        return Result.ok(PageUtils.of(pushLogMapper, wrapper,
                query.getPageNum(), query.getPageSize(), PushLogVO::from));
    }
}
