package com.erp.system.authcenter.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.framework.context.OrgContextHolder;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserSyncResultVO sync() {
        List<AuthCenterUserDTO> users = authCenterClient.fetchUsers(null);
        UserSyncResultVO result = new UserSyncResultVO();
        result.setTotal(users.size());
        Long superRoleId = superRoleId();
        Set<String> centerEmployeeNos = new HashSet<>();
        for (AuthCenterUserDTO dto : users) {
            if (!StringUtils.hasText(dto.getEmployeeNo())) {
                continue;
            }
            centerEmployeeNos.add(dto.getEmployeeNo());
            SysUser local = userMapper.selectByEmployeeNo(dto.getEmployeeNo());
            boolean active = Boolean.TRUE.equals(dto.getActive());
            if (local == null) {
                if (!active) {
                    // 未建档且已失效：仅计数，不落死数据
                    result.setDisabled(result.getDisabled() + 1);
                    continue;
                }
                userMapper.insert(buildNew(dto));
                result.setAdded(result.getAdded() + 1);
                local = userMapper.selectByEmployeeNo(dto.getEmployeeNo());
            } else {
                local.setNickname(StringUtils.hasText(dto.getRealName()) ? dto.getRealName() : local.getNickname());
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
            }
        }
        result.setOrphanDisabled(disableOrphans(centerEmployeeNos, superRoleId));
        log.info("[AUTH-SYNC] 认证中心用户同步完成: total={}, added={}, updated={}, disabled={}, orphanDisabled={}",
                result.getTotal(), result.getAdded(), result.getUpdated(), result.getDisabled(),
                result.getOrphanDisabled());
        return result;
    }

    /**
     * 孤儿对账：本地同步副本（employee_no 非空）不在中心全量列表 = 中心已回收分配/已删除，
     * 以中心列表为准禁用（含收回超管映射），保证"中心回收 → 本地失效"闭环。
     */
    private int disableOrphans(Set<String> centerEmployeeNos, Long superRoleId) {
        int orphan = 0;
        for (SysUser local : userMapper.selectAllSyncedUsers()) {
            if (centerEmployeeNos.contains(local.getEmployeeNo())) {
                continue;
            }
            if (local.getStatus() == null || local.getStatus() != 0) {
                local.setStatus(0);
                userMapper.updateById(local);
            }
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
        SysUser user = buildNew(dto);
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
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.ofKey("erp.system.sso.user-not-exists", userId);
        }
        if (StringUtils.hasText(user.getEmployeeNo())) {
            // 中心是事实源：先调中心应用级启停，成功后本地镜像翻转
            if (enabled) {
                authCenterClient.enableUser(user.getEmployeeNo());
            } else {
                authCenterClient.disableUser(user.getEmployeeNo());
            }
        } else {
            log.warn("[AUTH-STATUS] 用户{}({})无工号（本地降级账号），仅本地启停", user.getUsername(), userId);
        }
        user.setStatus(enabled ? 1 : 0);
        userMapper.updateById(user);
        log.info("[AUTH-STATUS] 应用级{}: userId={}, employeeNo={}", enabled ? "启用" : "禁用", userId,
                user.getEmployeeNo());
    }

    /** 新用户建档：随机密码占位（不可本地登录），默认租户/总厂，dataScope=SELF */
    private SysUser buildNew(AuthCenterUserDTO dto) {
        SysUser user = new SysUser();
        user.setEmployeeNo(dto.getEmployeeNo());
        user.setUsername(dto.getEmployeeNo());
        user.setNickname(StringUtils.hasText(dto.getRealName()) ? dto.getRealName() : dto.getEmployeeNo());
        user.setPassword(passwordEncoder.encode(randomPassword()));
        user.setOrgId(OrgContextHolder.DEFAULT_ORG_ID);
        user.setTenantId(OrgContextHolder.DEFAULT_TENANT_ID);
        user.setDataScope("SELF");
        user.setStatus(1);
        user.setRemark("认证中心同步（" + dto.getOrgName() + "/" + dto.getPosition() + "）");
        return user;
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
