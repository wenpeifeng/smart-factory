package com.erp.notify.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.framework.security.RequirePerm;
import com.erp.notify.core.NotifyService;
import com.erp.notify.dto.ChannelForm;
import com.erp.notify.dto.ChannelQuery;
import com.erp.notify.dto.ChannelVO;
import com.erp.notify.dto.SendLogVO;
import com.erp.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知通道管理接口（管理门户 /admin/system/notify-channels）。
 * <p>
 * 凭据返回一律脱敏（仅末 4 位）；修改时空/掩码串表示保持原值。
 * 权限码：system:notify:{list,edit,delete,test}。
 */
@Tag(name = "通知通道")
@RestController
@RequestMapping("/notify/channels")
@RequiredArgsConstructor
@Validated
public class NotifyChannelController {

    private final NotifyService notifyService;

    /** 通道分页 */
    @Operation(summary = "通道分页")
    @RequirePerm("system:notify:list")
    @GetMapping
    public Result<PageResult<ChannelVO>> page(@ModelAttribute ChannelQuery query) {
        return Result.ok(notifyService.pageChannels(query));
    }

    /** 新增通道 */
    @Operation(summary = "新增通道")
    @OperLog(module = "通知中心", operation = "新增通道")
    @RequirePerm("system:notify:edit")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ChannelForm form) {
        return Result.ok(notifyService.createChannel(form));
    }

    /** 修改通道 */
    @Operation(summary = "修改通道")
    @OperLog(module = "通知中心", operation = "修改通道")
    @RequirePerm("system:notify:edit")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ChannelForm form) {
        notifyService.updateChannel(id, form);
        return Result.ok();
    }

    /** 启用/停用通道 */
    @Operation(summary = "启停通道")
    @OperLog(module = "通知中心", operation = "启停通道")
    @RequirePerm("system:notify:edit")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam int status) {
        notifyService.changeChannelStatus(id, status);
        return Result.ok();
    }

    /** 删除通道 */
    @Operation(summary = "删除通道")
    @OperLog(module = "通知中心", operation = "删除通道")
    @RequirePerm("system:notify:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        notifyService.deleteChannel(id);
        return Result.ok();
    }

    /** 测试发送（发一条测试 Markdown；钉钉/企微需指定接收人） */
    @Operation(summary = "测试发送")
    @OperLog(module = "通知中心", operation = "通道测试发送")
    @RequirePerm("system:notify:test")
    @PostMapping("/{id}/test")
    public Result<SendLogVO> test(@PathVariable Long id,
                                  @RequestParam(required = false) Long receiverUserId) {
        return Result.ok(notifyService.testSend(id, receiverUserId));
    }
}
