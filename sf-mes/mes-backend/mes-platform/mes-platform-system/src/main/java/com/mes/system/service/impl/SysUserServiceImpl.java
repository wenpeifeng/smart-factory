package com.mes.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.system.dto.auth.OrgVO;
import com.mes.system.dto.user.UserQuery;
import com.mes.system.dto.user.UserVO;
import com.mes.system.entity.SysDept;
import com.mes.system.entity.SysRole;
import com.mes.system.entity.SysUser;
import com.mes.system.entity.SysUserOrg;
import com.mes.system.entity.SysUserRole;
import com.mes.system.mapper.OrgDirectoryMapper;
import com.mes.system.mapper.SysDeptMapper;
import com.mes.system.mapper.SysRoleMapper;
import com.mes.system.mapper.SysUserMapper;
import com.mes.system.mapper.SysUserOrgMapper;
import com.mes.system.mapper.SysUserRoleMapper;
import com.mes.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户管理服务实现（只读 + 角色分配）。
 * <p>主账号生命周期（建号/改密/离职/档案变更/启停）由 auth-center 统一管控：
 * 本系统用户来自同步副本（employee_no 工号匹配，见 integration 模块三层同步），
 * 应用级启停走 PUT /integration/auth/users/{employeeNo}/status（仅影响本系统登录，
 * 不动该用户在其他系统的权限）。历史上的 create/update/delete/resetPassword/updateStatus
 * 接口已按该边界决策移除。
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserOrgMapper userOrgMapper;
    private final OrgDirectoryMapper orgDirectoryMapper;

    @Override
    public PageResult<UserVO> page(UserQuery query) {
        var wrapper = Wrappers.lambdaQuery(SysUser.class)
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(SysUser::getUsername, query.getKeyword())
                        .or().like(SysUser::getEmployeeNo, query.getKeyword())
                        .or().like(SysUser::getNickname, query.getKeyword()))
                .eq(query.getDeptId() != null, SysUser::getDeptId, query.getDeptId())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .orderByDesc(SysUser::getId);
        PageResult<UserVO> page = PageUtils.of(userMapper, wrapper,
                query.getPageNum(), query.getPageSize(), UserVO::from);
        if (!page.getRecords().isEmpty()) {
            fillDeptAndRoles(page.getRecords());
        }
        return page;
    }

    @Override
    public UserVO getDetail(Long id) {
        SysUser user = requireUser(id);
        UserVO vo = UserVO.from(user);
        fillDeptAndRoles(List.of(vo));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, List<Long> roleIds) {
        requireUser(id);
        // 整体覆盖：物理清空后重建（避免逻辑删在 uk(user_id, role_id) 撞键堆积）
        userRoleMapper.delete(Wrappers.lambdaQuery(SysUserRole.class)
                .eq(SysUserRole::getUserId, id));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        Set<Long> validIds = roleMapper.selectBatchIds(roleIds).stream()
                .map(SysRole::getId).collect(Collectors.toSet());
        List<Long> invalid = roleIds.stream().filter(r -> !validIds.contains(r)).toList();
        if (!invalid.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "角色不存在: %s", invalid);
        }
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(id);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    @Override
    public List<Long> getUserOrgIds(Long id) {
        return userOrgMapper.selectOrgIdsByUserId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignOrgs(Long id, List<Long> orgIds) {
        requireUser(id);
        // 绑定维护为整体覆盖：物理删除后重建（XML 物理删除，防 uk 撞键）
        userOrgMapper.deletePhysicallyByUserId(id);
        if (orgIds == null || orgIds.isEmpty()) {
            return;
        }
        List<Long> enabledIds = orgDirectoryMapper.selectEnabledOrgs().stream()
                .map(OrgVO::getId).collect(Collectors.toList());
        List<Long> invalid = orgIds.stream().filter(o -> !enabledIds.contains(o)).toList();
        if (!invalid.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "组织不存在或未启用: %s", invalid);
        }
        for (Long orgId : orgIds) {
            SysUserOrg binding = new SysUserOrg();
            binding.setUserId(id);
            binding.setOrgId(orgId);
            userOrgMapper.insert(binding);
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
        return roleMapper.selectBatchIds(roleIds).stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                .map(SysRole::getCode)
                .toList();
    }

    /** 回填部门名与角色聚合（列表/详情共用） */
    private void fillDeptAndRoles(List<UserVO> records) {
        List<Long> deptIds = records.stream().map(UserVO::getDeptId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> deptNames = deptIds.isEmpty() ? Map.of()
                : deptMapper.selectBatchIds(deptIds).stream()
                        .collect(Collectors.toMap(SysDept::getId, SysDept::getName));
        List<Long> userIds = records.stream().map(UserVO::getId).toList();
        Map<Long, List<Long>> roleIdsByUser = userRoleMapper.selectList(
                        Wrappers.lambdaQuery(SysUserRole.class).in(SysUserRole::getUserId, userIds))
                .stream().collect(Collectors.groupingBy(SysUserRole::getUserId,
                        Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())));
        List<Long> allRoleIds = roleIdsByUser.values().stream()
                .flatMap(Collection::stream).distinct().toList();
        Map<Long, SysRole> roleMap = allRoleIds.isEmpty() ? Map.of()
                : roleMapper.selectBatchIds(allRoleIds).stream()
                        .collect(Collectors.toMap(SysRole::getId, Function.identity()));
        records.forEach(vo -> {
            if (vo.getDeptId() != null) {
                vo.setDeptName(deptNames.get(vo.getDeptId()));
            }
            List<Long> roleIds = roleIdsByUser.getOrDefault(vo.getId(), List.of());
            vo.setRoleIds(roleIds);
            vo.setRoleNames(roleIds.stream()
                    .map(roleMap::get).filter(Objects::nonNull)
                    .map(SysRole::getName).toList());
        });
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "用户不存在: %d".formatted(id));
        }
        return user;
    }
}
