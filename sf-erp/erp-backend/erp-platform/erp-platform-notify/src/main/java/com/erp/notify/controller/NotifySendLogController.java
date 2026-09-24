package com.erp.notify.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.framework.security.RequirePerm;
import com.erp.notify.core.NotifyService;
import com.erp.notify.dto.SendLogQuery;
import com.erp.notify.dto.SendLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知发送日志接口（管理门户通道页"发送日志"Drawer）。
 */
@Tag(name = "通知发送日志")
@RestController
@RequestMapping("/notify/send-logs")
@RequiredArgsConstructor
public class NotifySendLogController {

    private final NotifyService notifyService;

    /** 发送日志分页 */
    @Operation(summary = "发送日志分页")
    @RequirePerm("system:notify:list")
    @GetMapping
    public Result<PageResult<SendLogVO>> page(@ModelAttribute SendLogQuery query) {
        return Result.ok(notifyService.pageSendLogs(query));
    }
}
