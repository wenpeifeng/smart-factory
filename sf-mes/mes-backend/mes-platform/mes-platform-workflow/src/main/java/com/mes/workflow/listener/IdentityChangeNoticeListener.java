package com.mes.workflow.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.framework.event.IdentityChangeEvent;
import com.mes.system.entity.SysRole;
import com.mes.system.entity.SysUser;
import com.mes.system.entity.SysUserRole;
import com.mes.system.mapper.SysRoleMapper;
import com.mes.system.mapper.SysUserMapper;
import com.mes.system.mapper.SysUserRoleMapper;
import com.mes.workflow.entity.WfMessage;
import com.mes.workflow.service.WfMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** 将集团人员变更转成 MES 管理员可见的站内消息。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityChangeNoticeListener {

    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final WfMessageService messageService;

    @EventListener
    public void onIdentityChange(IdentityChangeEvent event) {
        try {
            for (Long receiverId : adminUserIds()) {
                messageService.push(receiverId, WfMessage.TYPE_IDENTITY, title(event), content(event),
                        "/admin/system/users");
            }
        } catch (Exception ex) {
            log.warn("人员变更通知写入失败，不影响身份同步: {}", ex.getMessage());
        }
    }

    private List<Long> adminUserIds() {
        SysRole role = roleMapper.selectOne(Wrappers.lambdaQuery(SysRole.class)
                .eq(SysRole::getCode, "super_admin").last("LIMIT 1"));
        if (role == null) return List.of();
        List<Long> ids = userRoleMapper.selectList(Wrappers.lambdaQuery(SysUserRole.class)
                        .eq(SysUserRole::getRoleId, role.getId())).stream()
                .map(SysUserRole::getUserId).distinct().toList();
        if (ids.isEmpty()) return List.of();
        return userMapper.selectBatchIds(ids).stream().filter(u -> u.getStatus() != null && u.getStatus() == 1)
                .map(SysUser::getId).toList();
    }

    private String title(IdentityChangeEvent event) {
        String source = IdentityChangeEvent.SOURCE_GROUP.equals(event.source()) ? "集团通知" : "本系统操作";
        String action = switch (event.changeType()) {
            case IdentityChangeEvent.ASSIGNED -> "新员工已分配到 MES";
            case IdentityChangeEvent.LEFT -> "员工已离职";
            case IdentityChangeEvent.DISABLED -> "员工已停用";
            case IdentityChangeEvent.ENABLED -> "员工已恢复使用";
            default -> "员工已从 MES 移除";
        };
        return "[" + source + "] " + action;
    }

    private String content(IdentityChangeEvent event) {
        String contact = event.phone() != null && !event.phone().isBlank() ? event.phone()
                : event.email() != null && !event.email().isBlank() ? event.email() : "-";
        return "操作时间：" + value(event.operationTime() == null ? null
                : event.operationTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                + "；员工：" + value(event.realName()) + "（" + value(event.employeeNo()) + "）"
                + "；入职日期：" + value(event.entryDate()) + "；岗位：" + value(event.position())
                + "；联系方式：" + contact;
    }

    private String value(Object value) {
        return value == null || value.toString().isBlank() ? "-" : value.toString();
    }
}
