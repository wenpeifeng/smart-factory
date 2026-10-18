package com.erp.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;

import com.erp.system.dto.user.UserScopeForm;
import com.erp.system.dto.user.UserQuery;
import com.erp.system.dto.user.UserVO;
import com.erp.system.entity.SysDept;
import com.erp.system.entity.SysRole;
import com.erp.system.entity.SysUser;
import com.erp.system.entity.SysUserOrg;
import com.erp.system.entity.SysUserRole;
import com.erp.system.mapper.OrgDirectoryMapper;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserOrgMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import com.erp.system.security.SecurityUtils;
import com.erp.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户管理服务实现。
 * <p>
 * 安全约束：口令仅存 BCrypt 密文且任何出参（UserVO）不含 password 字段；
 * 内置管理员（id=1）与当前登录人自身不可删除/停用，防止系统失去入口。
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    /** 内置超管用户 ID（V1 种子数据的 admin） */
    private static final long ADMIN_USER_ID = 1L;

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserOrgMapper userOrgMapper;
    private final OrgDirectoryMapper orgDirectoryMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<UserVO> page(UserQuery query) {
        var wrapper = Wrappers.lambdaQuery(SysUser.class)
                .eq(SysUser::getAuthAssigned, true)
                .and(notBlank(query.getKeyword()), w -> w
                        .like(SysUser::getUsername, query.getKeyword().trim())
                        .or().like(SysUser::getEmployeeNo, query.getKeyword().trim())
                        .or().like(SysUser::getNickname, query.getKeyword().trim())
                        .or().like(SysUser::getPosition, query.getKeyword().trim()))
                .eq(query.getDeptId() != null, SysUser::getDeptId, query.getDeptId())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .eq(query.getIdentityStatus() != null, SysUser::getIdentityStatus, query.getIdentityStatus())
                .ge(query.getEntryDateStart() != null, SysUser::getEntryDate, query.getEntryDateStart())
                .le(query.getEntryDateEnd() != null, SysUser::getEntryDate, query.getEntryDateEnd())
                .last(userOrderBy(query));
        PageResult<UserVO> page = PageUtils.of(userMapper, wrapper,
                query.getPageNum(), query.getPageSize(), UserVO::from);
        fillDeptAndRoles(page.getRecords());
        return page;
    }

    /** 白名单排序：无论用户选择何种排序，离职人员始终置底。 */
    private String userOrderBy(UserQuery query) {
        String column = switch (query.getSortField() == null ? "entryDate" : query.getSortField()) {
            case "nickname" -> "nickname";
            case "employeeNo" -> "employee_no";
            case "identityCreateTime" -> "identity_create_time";
            case "status" -> "status";
            default -> "entry_date";
        };
        String direction = "asc".equalsIgnoreCase(query.getSortOrder()) ? "ASC" : "DESC";
        return "ORDER BY CASE WHEN identity_status = 2 THEN 1 ELSE 0 END ASC, "
                + column + " " + direction + ", id DESC";
    }

    @Override
    public UserVO getDetail(Long id) {
        SysUser user = requireUser(id);
        UserVO vo = UserVO.from(user);
        fillDeptAndRoles(List.of(vo));
        return vo;
    }
    @Override
    public void updateScope(Long id, UserScopeForm form) {
        SysUser user = requireAssignedUser(id);
        user.setDataScope(form.getDataScope());
        if (form.getOrgId() != null) {
            user.setOrgId(form.getOrgId());
        }
        user.setDeptId(form.getDeptId());
        userMapper.updateById(user);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, List<Long> roleIds) {
        requireAssignedUser(id);
        List<Long> distinct = roleIds == null ? List.of() : roleIds.stream().distinct().toList();
        if (!distinct.isEmpty()) {
            Long bound = roleMapper.selectCount(Wrappers.lambdaQuery(SysRole.class).in(SysRole::getId, distinct));
            if (bound != distinct.size()) {
                throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.system.invalid-roles");
            }
        }
        userRoleMapper.deletePhysicallyByUserId(id);
        if (!distinct.isEmpty()) {
            insertUserRoles(id, distinct);
        }
    }

    @Override
    public List<String> getRoleCodesByUserId(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(Wrappers.lambdaQuery(SysUserRole.class)
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(roleIds).stream().map(SysRole::getCode).toList();
    }

    @Override
    public List<Long> getUserOrgIds(Long id) {
        requireAssignedUser(id);
        return userOrgMapper.selectOrgIdsByUserId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignOrgs(Long id, List<Long> orgIds) {
        requireAssignedUser(id);
        List<Long> distinct = orgIds == null ? List.of() : orgIds.stream()
                .filter(Objects::nonNull).distinct().toList();
        if (!distinct.isEmpty()) {
            // 绑定目标必须是启用组织：防越权绑定到停用/不存在组织
            Set<Long> enabledIds = orgDirectoryMapper.selectEnabledOrgs().stream()
                    .map(org -> org.getId()).collect(Collectors.toSet());
            if (!enabledIds.containsAll(distinct)) {
                throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.system.invalid-orgs");
            }
        }
        userOrgMapper.deletePhysicallyByUserId(id);
        if (!distinct.isEmpty()) {
            Long currentUserId = SecurityUtils.getCurrentUserIdOrDefault(0L);
            for (Long orgId : distinct) {
                SysUserOrg binding = new SysUserOrg();
                binding.setUserId(id);
                binding.setOrgId(orgId);
                binding.setCreateBy(currentUserId);
                userOrgMapper.insert(binding);
            }
        }
    }

    /** 批量写入用户-角色关联 */
    private void insertUserRoles(Long userId, List<Long> roleIds) {
        Long currentUserId = SecurityUtils.getCurrentUserIdOrDefault(0L);
        for (Long roleId : roleIds.stream().distinct().toList()) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRole.setCreateBy(currentUserId);
            userRoleMapper.insert(userRole);
        }
    }

    /** 校验账号未被占用（排除自身） */
    private void assertUsernameUnused(String username, Long excludeId) {
        Long count = userMapper.selectCount(Wrappers.lambdaQuery(SysUser.class)
                .eq(SysUser::getUsername, username)
                .ne(excludeId != null, SysUser::getId, excludeId));
        if (count != null && count > 0) {
            throw BusinessException.ofKey("erp.system.username-exists", username);
        }
    }

    /** 按主键取用户，不存在抛业务异常 */
    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.system.user-not-exists");
        }
        return user;
    }

    private SysUser requireAssignedUser(Long id) {
        SysUser user = requireUser(id);
        if (!Boolean.TRUE.equals(user.getAuthAssigned())) {
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "仅可管理认证中心已分配到本系统的员工");
        }
        return user;
    }

    /** 为 VO 列表批量回填部门名与角色（避免 N+1：每页最多三次批量查询） */
    private void fillDeptAndRoles(List<UserVO> vos) {
        if (vos.isEmpty()) {
            return;
        }
        // 部门名
        Set<Long> deptIds = vos.stream().map(UserVO::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNames = deptIds.isEmpty() ? Map.of()
                : deptMapper.selectBatchIds(deptIds).stream()
                        .collect(Collectors.toMap(SysDept::getId, SysDept::getName));
        // 用户-角色关联
        List<Long> userIds = vos.stream().map(UserVO::getId).toList();
        List<SysUserRole> relations = userRoleMapper.selectList(Wrappers.lambdaQuery(SysUserRole.class)
                .in(SysUserRole::getUserId, userIds));
        Map<Long, List<SysUserRole>> byUser = relations.stream()
                .collect(Collectors.groupingBy(SysUserRole::getUserId));
        Set<Long> roleIds = relations.stream().map(SysUserRole::getRoleId).collect(Collectors.toSet());
        Map<Long, SysRole> roleMap = roleIds.isEmpty() ? Map.of()
                : roleMapper.selectBatchIds(roleIds).stream()
                        .collect(Collectors.toMap(SysRole::getId, Function.identity()));
        for (UserVO vo : vos) {
            if (vo.getDeptId() != null) {
                vo.setDeptName(deptNames.get(vo.getDeptId()));
            }
            List<SysUserRole> rel = byUser.getOrDefault(vo.getId(), List.of());
            vo.setRoleIds(rel.stream().map(SysUserRole::getRoleId).toList());
            vo.setRoleNames(rel.stream()
                    .map(r -> roleMap.get(r.getRoleId()))
                    .filter(Objects::nonNull)
                    .map(SysRole::getName)
                    .toList());
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private int defaultStatus(Integer status) {
        return status == null ? 1 : status;
    }
}
