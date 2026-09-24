package com.erp.workflow.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.workflow.dto.query.WfMessageQuery;
import com.erp.workflow.dto.vo.WfMessageVO;
import com.erp.workflow.service.WfMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 消息中心接口（接收人=当前登录人，全部接口自带越权防护）。
 */
@Tag(name = "消息中心")
@RestController
@RequestMapping("/workflow/messages")
@RequiredArgsConstructor
public class WfMessageController {

    private final WfMessageService messageService;

    /** 我的消息分页（unreadOnly=true 仅未读） */
    @Operation(summary = "我的消息")
    @RequirePerm("workflow:message:list")
    @GetMapping
    public Result<PageResult<WfMessageVO>> page(@Valid @ModelAttribute WfMessageQuery query) {
        return Result.ok(messageService.pageMine(query));
    }

    /** 我的未读消息数（菜单角标） */
    @Operation(summary = "未读消息数")
    @RequirePerm("workflow:message:list")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(messageService.unreadCount());
    }

    /** 标记单条已读 */
    @Operation(summary = "标记已读")
    @RequirePerm("workflow:message:edit")
    @OperLog(module = "工作流", operation = "标记消息已读")
    @Idempotent
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return Result.ok();
    }

    /** 全部已读 */
    @Operation(summary = "全部已读")
    @RequirePerm("workflow:message:edit")
    @OperLog(module = "工作流", operation = "全部标记已读")
    @Idempotent
    @PutMapping("/read-all")
    public Result<Integer> markAllRead() {
        return Result.ok(messageService.markAllRead());
    }
}
