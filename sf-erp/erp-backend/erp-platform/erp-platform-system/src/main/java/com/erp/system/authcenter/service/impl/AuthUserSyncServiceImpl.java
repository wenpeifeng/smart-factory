package com.erp.system.authcenter.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.framework.context.OrgContextHolder;
import com.erp.framework.event.IdentityChangeEvent;
import com.erp.system.authcenter.client.AuthCenterClient;
import com.erp.system.authcenter.dto.AuthCenterUserDTO;
import com.erp.system.authcenter.dto.UserSyncResultVO;
import com.erp.system.authcenter.mapper.AuthCenterUserMapper;
import com.erp.system.authcenter.service.AuthUserSyncService;
import com.erp.system.entity.SysRole;
import com.erp.system.entity.SysUser;
import com.erp.system.entity.SysUserRole;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 认证中心用户同步实现（对账式）。
 * <p>规则：
 * <ul>
 *   <li>匹配键 = 工号 employee_no（不可变，本地新列 sys_user.employee_no）；</li>
 *   <li>新增用户：username=employee_no、nickname=name、随机密码占位（SSO 用户
 *       不可本地账密登录）、status 启用、默认租户/总厂；</li>
 *   <li>active=false（LEFT/SUSPENDED/APP_DISABLED）→ status=0 软禁用（不物理删）；</li>
 *   <li>roles 含 erp.admin → 授本地内置超管角色 super_admin（对账双向增删）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthUserSyncServiceImpl implements AuthUserSyncService {

    /** 本地内置超管角色编码（erp.admin 标签的落点） */
    private static final String SUPER_ADMIN_CODE = "super_admin";

    /** 认证中心授予本系统超管的角色标签 */
    private static final String ADMIN_ROLE_TAG = "erp.admin";

    private final AuthCenterClient authCenterClient;
    private final AuthCenterUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserSyncResultVO sync(String source) {
        String eventSource = IdentityChangeEvent.SOURCE_LOCAL.equals(source)
                ? IdentityChangeEvent.SOURCE_LOCAL : IdentityChangeEvent.SOURCE_GROUP;
        List<AuthCenterUserDTO> users = authCenterClient.fetchUsers(null);
        UserSyncResultVO result = new UserSyncResultVO();
        result.setTotal(users.size());
        Long superRoleId = superRoleId();
        Set<String> centerEmployeeNos = new HashSet<>();
        for (AuthCenterUserDTO dto : users) {
            if (!StringUtils.hasText(dto.getEmployeeNo())) {
                continue;
            }
            boolean assigned = !"REVOKED".equals(dto.getInactiveReason());
            if (assigned) {
                centerEmployeeNos.add(dto.getEmployeeNo());
            }
            SysUser local = userMapper.selectByEmployeeNo(dto.getEmployeeNo());
            boolean existed = local != null;
            boolean wasAssigned = existed && Boolean.TRUE.equals(local.getAuthAssigned());
            int previousStatus = existed && local.getStatus() != null ? local.getStatus() : -1;
            Integer previousIdentityStatus = existed ? local.getIdentityStatus() : null;
            boolean active = Boolean.TRUE.equals(dto.getActive());
            if (local == null) {
                if (!assigned) {
                    // 已回收且本地从未建档：不落死数据
                    result.setDisabled(result.getDisabled() + 1);
                    continue;
                }
                userMapper.insert(buildNew(dto, active));
                result.setAdded(result.getAdded() + 1);
                local = userMapper.selectByEmployeeNo(dto.getEmployeeNo());
            } else {
                local.setNickname(StringUtils.hasText(dto.getRealName()) ? dto.getRealName() : local.getNickname());
                local.setAuthAssigned(assigned);
                local.setIdentitySource(dto.getAccountSource());
                local.setIdentityImmutable(Boolean.TRUE.equals(dto.getImmutable()));
                copyIdentityLifecycle(local, dto);
                local.setEmail(dto.getEmail());
                local.setPhone(dto.getPhone());
                local.setPosition(dto.getPosition());
                local.setStatus(active ? 1 : 0);
                userMapper.updateById(local);
                if (active) {
                    result.setUpdated(result.getUpdated() + 1);
                } else {
                    result.setDisabled(result.getDisabled() + 1);
                }
            }
            if (local != null) {
                reconcileAdminRole(local.getId(), superRoleId, active && dto.getRoles() != null
                        && dto.getRoles().contains(ADMIN_ROLE_TAG));
                publishTransition(dto, existed, wasAssigned, previousStatus, previousIdentityStatus,
                        assigned, active, eventSource);
            }
        }
        result.setOrphanDisabled(disableOrphans(centerEmployeeNos, superRoleId, eventSource));
        log.info("[AUTH-SYNC] 认证中心用户同步完成: total={}, added={}, updated={}, disabled={}, orphanDisabled={}",
                result.getTotal(), result.getAdded(), result.getUpdated(), result.getDisabled(),
                result.getOrphanDisabled());
        return result;
    }

    /**
     * 孤儿对账：本地同步副本（employee_no 非空）不在中心全量列表 = 中心已回收分配/已删除，
     * 以中心列表为准禁用（含收回超管映射），保证"中心回收 → 本地失效"闭环。
     */
    private int disableOrphans(Set<String> centerEmployeeNos, Long superRoleId, String source) {
        int orphan = 0;
        for (SysUser local : userMapper.selectAllSyncedUsers()) {
            if (centerEmployeeNos.contains(local.getEmployeeNo())) {
                continue;
            }
            publishChange(local, IdentityChangeEvent.REVOKED, source,
                    local.getIdentityUpdateTime());
            if (local.getStatus() == null || local.getStatus() != 0) {
                local.setStatus(0);
            }
            local.setAuthAssigned(false);
            userMapper.updateById(local);
            reconcileAdminRole(local.getId(), superRoleId, false);
            orphan++;
        }
        return orphan;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser jitCreate(String employeeNo, String realName, boolean adminTag) {
        AuthCenterUserDTO dto = new AuthCenterUserDTO();
        dto.setEmployeeNo(employeeNo);
        dto.setRealName(realName);
        dto.setActive(true);
        dto.setIdentityStatus(1);
        dto.setIdentityCreateTime(LocalDateTime.now());
        dto.setUpdateTime(LocalDateTime.now());
        SysUser user = buildNew(dto, true);
        user.setRemark("认证中心 JIT 建档（SSO 登录）");
        userMapper.insert(user);
        if (adminTag) {
            reconcileAdminRole(user.getId(), superRoleId(), true);
        }
        log.info("[SSO-JIT] 登录即时建档: employeeNo={}, userId={}, admin={}", employeeNo, user.getId(), adminTag);
        return user;
    }

    @Override
    public void changeAppStatus(Long userId, boolean enabled) {
        SysUser user = requireAssignedUser(userId);
        // 中心是事实源：先调中心应用级启停，成功后本地镜像翻转
        if (enabled) {
            authCenterClient.enableUser(user.getEmployeeNo());
        } else {
            authCenterClient.disableUser(user.getEmployeeNo());
        }
        sync(IdentityChangeEvent.SOURCE_LOCAL);
        log.info("[AUTH-STATUS] 应用级{}: userId={}, employeeNo={}", enabled ? "启用" : "禁用", userId,
                user.getEmployeeNo());
    }

    @Override
    public void updateProfile(Long userId, AuthUserSyncService.ProfileForm form) {
        SysUser user = requireAssignedUser(userId);
        authCenterClient.updateProfile(user.getEmployeeNo(), new AuthCenterClient.ProfileForm(
                form.realName(), form.position(), form.email(), form.phone()));
        sync(IdentityChangeEvent.SOURCE_LOCAL);
    }

    @Override
    public void resetPassword(Long userId, String password) {
        SysUser user = requireAssignedUser(userId);
        authCenterClient.resetPassword(user.getEmployeeNo(), password);
    }

    private SysUser requireAssignedUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.ofKey("erp.system.sso.user-not-exists", userId);
        }
        if (!Boolean.TRUE.equals(user.getAuthAssigned()) || !StringUtils.hasText(user.getEmployeeNo())) {
            throw new BusinessException("该账号不是认证中心当前分配的员工，不能在用户管理中操作");
        }
        return user;
    }

    /** 新用户建档：随机密码占位（不可本地登录），默认租户/总厂，dataScope=SELF */
    private SysUser buildNew(AuthCenterUserDTO dto, boolean active) {
        SysUser user = new SysUser();
        user.setEmployeeNo(dto.getEmployeeNo());
        user.setAuthAssigned(true);
        user.setIdentitySource(dto.getAccountSource());
        user.setIdentityImmutable(Boolean.TRUE.equals(dto.getImmutable()));
        copyIdentityLifecycle(user, dto);
        user.setUsername(dto.getEmployeeNo());
        user.setNickname(StringUtils.hasText(dto.getRealName()) ? dto.getRealName() : dto.getEmployeeNo());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setPosition(dto.getPosition());
        user.setPassword(passwordEncoder.encode(randomPassword()));
        user.setOrgId(OrgContextHolder.DEFAULT_ORG_ID);
        user.setTenantId(OrgContextHolder.DEFAULT_TENANT_ID);
        user.setDataScope("SELF");
        user.setStatus(active ? 1 : 0);
        user.setRemark("认证中心同步（" + dto.getOrgName() + "/" + dto.getPosition() + "）");
        return user;
    }

    private void copyIdentityLifecycle(SysUser user, AuthCenterUserDTO dto) {
        user.setIdentityStatus(dto.getIdentityStatus() != null ? dto.getIdentityStatus()
                : Boolean.TRUE.equals(dto.getActive()) ? 1 : 3);
        user.setIdentityInactiveReason(dto.getInactiveReason());
        user.setEntryDate(dto.getEntryDate());
        user.setLeaveDate(dto.getLeaveDate());
        user.setIdentityCreateTime(dto.getIdentityCreateTime());
        user.setIdentityUpdateTime(dto.getUpdateTime());
    }

    /** 只在状态跨越时发布，每分钟全量对账不会重复刷消息。 */
    private void publishTransition(AuthCenterUserDTO dto, boolean existed, boolean wasAssigned,
                                   int previousStatus, Integer previousIdentityStatus,
                                   boolean assigned, boolean active, String source) {
        String type = null;
        if (!existed && assigned) type = IdentityChangeEvent.ASSIGNED;
        else if (wasAssigned && !assigned) type = IdentityChangeEvent.REVOKED;
        else if (Integer.valueOf(2).equals(dto.getIdentityStatus())
                && !Integer.valueOf(2).equals(previousIdentityStatus)) type = IdentityChangeEvent.LEFT;
        else if (previousStatus == 1 && !active) type = IdentityChangeEvent.DISABLED;
        else if (previousStatus == 0 && active) type = IdentityChangeEvent.ENABLED;
        if (type != null) {
            eventPublisher.publishEvent(new IdentityChangeEvent(type, source,
                    dto.getEmployeeNo(), dto.getRealName(), dto.getPosition(), dto.getPhone(), dto.getEmail(),
                    dto.getEntryDate(), dto.getUpdateTime() == null ? LocalDateTime.now() : dto.getUpdateTime()));
        }
    }

    private void publishChange(SysUser user, String type, String source, LocalDateTime operationTime) {
        eventPublisher.publishEvent(new IdentityChangeEvent(type, source, user.getEmployeeNo(), user.getNickname(),
                user.getPosition(), user.getPhone(), user.getEmail(), user.getEntryDate(),
                operationTime == null ? LocalDateTime.now() : operationTime));
    }

    /** erp.admin 标签 ↔ 本地 super_admin 角色对账（应授未授补、应收回删） */
    private void reconcileAdminRole(Long userId, Long superRoleId, boolean shouldBeAdmin) {
        if (superRoleId == null) {
            return;
        }
        boolean hasMapping = userRoleMapper.selectCount(Wrappers.lambdaQuery(SysUserRole.class)
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, superRoleId)) > 0;
        if (shouldBeAdmin && !hasMapping) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(superRoleId);
            userRoleMapper.insert(ur);
        } else if (!shouldBeAdmin && hasMapping) {
            userRoleMapper.delete(Wrappers.lambdaQuery(SysUserRole.class)
                    .eq(SysUserRole::getUserId, userId)
                    .eq(SysUserRole::getRoleId, superRoleId));
        }
    }

    /** 内置超管角色 ID（缺失时跳过角色对账，不影响用户同步） */
    private Long superRoleId() {
        SysRole role = roleMapper.selectOne(Wrappers.lambdaQuery(SysRole.class)
                .eq(SysRole::getCode, SUPER_ADMIN_CODE)
                .last("LIMIT 1"));
        return role == null ? null : role.getId();
    }

    /** 32 位随机可打印密码占位（BCrypt 落库，任何人不可知） */
    private String randomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append((char) ('!' + random.nextInt(90)));
        }
        return sb.toString();
    }
}
