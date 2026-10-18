package com.mes.integration.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.framework.event.IdentityChangeEvent;
import com.mes.integration.authcenter.AuthCenterClient;
import com.mes.integration.authcenter.dto.AuthCenterUserDTO;
import com.mes.integration.authcenter.dto.UserSyncResultVO;
import com.mes.integration.service.AuthUserSyncService;
import com.mes.system.entity.SysUser;
import com.mes.system.entity.SysUserRole;
import com.mes.system.mapper.SysUserMapper;
import com.mes.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * auth-center 用户同步实现（对账式，契约见 docs/业务系统接入指南.md §3.2）。
 * <p>规则：
 * <ul>
 *   <li>匹配键 = 工号 employeeNo（不可变），落在独立列 sys_user.employee_no（uk）；
 *       username 只存登录名（默认=工号，来自中心 username 字段，可分道扬镳）；</li>
 *   <li>新用户建档：随机口令占位（登录走 SSO）、dataScope=SELF；</li>
 *   <li>active=false（LEFT/SUSPENDED/APP_DISABLED）→ status=0 软删除副本；</li>
 *   <li>roles 含 mes.admin → 授本地超管角色（sys_role.code=super_admin），
 *       反之收回该映射（普通角色由 MES 管理端内部分配，不由同步触碰）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthUserSyncServiceImpl implements AuthUserSyncService {

    /** 本地超管角色编码（mes.admin 标签的落点） */
    private static final String SUPER_ROLE_CODE = "super_admin";

    private final AuthCenterClient authCenterClient;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final com.mes.system.mapper.SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserSyncResultVO sync(String source) {
        String eventSource = IdentityChangeEvent.SOURCE_LOCAL.equals(source)
                ? IdentityChangeEvent.SOURCE_LOCAL : IdentityChangeEvent.SOURCE_GROUP;
        List<AuthCenterUserDTO> users = authCenterClient.fetchUsers(null);
        UserSyncResultVO result = new UserSyncResultVO();
        result.setFetched(users.size());
        Long superRoleId = sysRoleMapper.selectOne(Wrappers.lambdaQuery(com.mes.system.entity.SysRole.class)
                .eq(com.mes.system.entity.SysRole::getCode, SUPER_ROLE_CODE)
                .last("LIMIT 1")).getId();
        java.util.Set<String> centerNos = new java.util.HashSet<>();
        for (AuthCenterUserDTO dto : users) {
            if (!StringUtils.hasText(dto.getEmployeeNo())) {
                continue;
            }
            boolean assigned = !"REVOKED".equals(dto.getInactiveReason());
            if (assigned) {
                centerNos.add(dto.getEmployeeNo());
            }
            boolean active = Boolean.TRUE.equals(dto.getActive());
            boolean mesAdmin = dto.getRoles() != null && dto.getRoles().contains("mes.admin");
            SysUser previous = sysUserMapper.selectOne(Wrappers.lambdaQuery(SysUser.class)
                    .eq(SysUser::getEmployeeNo, dto.getEmployeeNo()).last("LIMIT 1"));
            boolean existed = previous != null;
            boolean wasAssigned = existed && Boolean.TRUE.equals(previous.getAuthAssigned());
            int previousStatus = existed && previous.getStatus() != null ? previous.getStatus() : -1;
            Integer previousIdentityStatus = existed ? previous.getIdentityStatus() : null;
            if (!assigned && !existed) {
                result.setDisabled(result.getDisabled() + 1);
                continue;
            }
            upsert(dto, assigned, active, mesAdmin, superRoleId);
            publishTransition(dto, existed, wasAssigned, previousStatus, previousIdentityStatus,
                    assigned, active, eventSource);
            if (existed) {
                result.setUpdated(active ? result.getUpdated() + 1 : result.getDisabled() + 1);
            } else {
                result.setInserted(active ? result.getInserted() + 1 : result.getDisabled() + 1);
            }
        }
        // 孤儿对账：同步来源（remark 标记）的本地用户不在中心名单 → 禁用（中心已回收分配）
        for (SysUser local : sysUserMapper.selectList(Wrappers.lambdaQuery(SysUser.class)
                .eq(SysUser::getAuthAssigned, true))) {
            if (!centerNos.contains(local.getEmployeeNo())) {
                publishChange(local, IdentityChangeEvent.REVOKED, eventSource,
                        local.getIdentityUpdateTime());
                local.setStatus(0);
                local.setAuthAssigned(false);
                local.setIdentityInactiveReason("REVOKED");
                local.setIdentityUpdateTime(LocalDateTime.now());
                sysUserMapper.updateById(local);
                result.setDisabled(result.getDisabled() + 1);
                log.info("[AUTH-SYNC] 孤儿用户禁用（中心已回收）: {}", local.getEmployeeNo());
            }
        }
        log.info("[AUTH-SYNC] 用户同步完成: fetched={}, inserted={}, updated={}, disabled={}",
                result.getFetched(), result.getInserted(), result.getUpdated(), result.getDisabled());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser provision(String employeeNo, String username, String nickname, boolean mesAdmin) {
        Long superRoleId = sysRoleMapper.selectOne(Wrappers.lambdaQuery(com.mes.system.entity.SysRole.class)
                .eq(com.mes.system.entity.SysRole::getCode, SUPER_ROLE_CODE)
                .last("LIMIT 1")).getId();
        AuthCenterUserDTO dto = new AuthCenterUserDTO();
        dto.setEmployeeNo(employeeNo);
        dto.setUsername(username);
        dto.setRealName(nickname);
        dto.setActive(true);
        dto.setIdentityStatus(1);
        dto.setIdentityCreateTime(LocalDateTime.now());
        dto.setUpdateTime(LocalDateTime.now());
        return upsert(dto, true, true, mesAdmin, superRoleId);
    }

    /**
     * 单用户 upsert：建档（随机口令占位/SSO 标记）或刷新档案，并同步 mes.admin 角色映射。
     *
     * @return 落库后的本地用户
     */
    private SysUser upsert(AuthCenterUserDTO dto, boolean assigned, boolean active, boolean mesAdmin, Long superRoleId) {
        String employeeNo = dto.getEmployeeNo();
        SysUser local = sysUserMapper.selectOne(Wrappers.lambdaQuery(SysUser.class)
                .eq(SysUser::getEmployeeNo, employeeNo)
                .last("LIMIT 1"));
        if (local == null) {
            local = new SysUser();
            // 登录名取中心 username（默认=工号）；工号列专职做不可变身份键
            local.setUsername(StringUtils.hasText(dto.getUsername()) ? dto.getUsername() : employeeNo);
            local.setEmployeeNo(employeeNo);
            local.setAuthAssigned(assigned);
            local.setIdentitySource(dto.getAccountSource());
            local.setIdentityImmutable(Boolean.TRUE.equals(dto.getImmutable()));
            copyIdentityLifecycle(local, dto);
            local.setNickname(StringUtils.hasText(dto.getRealName()) ? dto.getRealName() : employeeNo);
            local.setEmail(dto.getEmail());
            local.setPhone(dto.getPhone());
            local.setPosition(dto.getPosition());
            SecureRandom random = new SecureRandom();
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < 32; i++) {
                sb.append((char) ('!' + random.nextInt(90)));
            }
            local.setPassword(passwordEncoder.encode(sb.toString()));
            local.setDataScope("SELF");
            local.setRemark("auth-center 同步");
            local.setStatus(active ? 1 : 0);
            sysUserMapper.insert(local);
        } else {
            if (StringUtils.hasText(dto.getRealName())) {
                local.setNickname(dto.getRealName());
            }
            local.setAuthAssigned(assigned);
            local.setIdentitySource(dto.getAccountSource());
            local.setIdentityImmutable(Boolean.TRUE.equals(dto.getImmutable()));
            copyIdentityLifecycle(local, dto);
            local.setEmail(dto.getEmail());
            local.setPhone(dto.getPhone());
            local.setPosition(dto.getPosition());
            local.setStatus(active ? 1 : 0);
            sysUserMapper.updateById(local);
        }
        // mes.admin 标签 -> 本地超管映射（增删均对账）
        boolean hasMapping = sysUserRoleMapper.selectCount(Wrappers.lambdaQuery(SysUserRole.class)
                .eq(SysUserRole::getUserId, local.getId())
                .eq(SysUserRole::getRoleId, superRoleId)) > 0;
        if (mesAdmin && active && !hasMapping) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(local.getId());
            ur.setRoleId(superRoleId);
            sysUserRoleMapper.insert(ur);
        } else if ((!mesAdmin || !active) && hasMapping) {
            sysUserRoleMapper.delete(Wrappers.lambdaQuery(SysUserRole.class)
                    .eq(SysUserRole::getUserId, local.getId())
                    .eq(SysUserRole::getRoleId, superRoleId));
        }
        return local;
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

    /** 只在人员跨状态时发送通知，保证分钟级全量对账幂等。 */
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
}
