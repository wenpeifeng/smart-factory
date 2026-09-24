package com.erp.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import com.erp.system.dto.role.RoleForm;
import com.erp.system.dto.role.RoleQuery;
import com.erp.system.dto.role.RoleVO;
import com.erp.system.entity.SysMenu;
import com.erp.system.entity.SysRole;
import com.erp.system.entity.SysRoleMenu;
import com.erp.system.entity.SysUser;
import com.erp.system.entity.SysUserRole;
import com.erp.system.mapper.SysMenuMapper;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysRoleMenuMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import com.erp.system.security.SecurityUtils;
import com.erp.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色管理服务实现。
 * <p>
 * 内置超管 super_admin：编码不可改、角色不可删（服务层同时对其全量放行菜单，
 * 见 {@link SysMenuServiceImpl}）。
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    /** 内置超管角色编码 */
    public static final String SUPER_ADMIN_CODE = "super_admin";

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;

    @Override
    public PageResult<RoleVO> page(RoleQuery query) {
        var wrapper = Wrappers.lambdaQuery(SysRole.class)
                .and(query.getKeyword() != null && !query.getKeyword().isBlank(), w -> w
                        .like(SysRole::getName, query.getKeyword().trim())
                        .or().like(SysRole::getCode, query.getKeyword().trim()))
                .eq(query.getStatus() != null, SysRole::getStatus, query.getStatus())
                .orderByAsc(SysRole::getSort).orderByAsc(SysRole::getId);
        return PageUtils.of(roleMapper, wrapper, query.getPageNum(), query.getPageSize(), RoleVO::from);
    }

    @Override
    public List<RoleVO> listAll() {
        return roleMapper.selectList(Wrappers.lambdaQuery(SysRole.class)
                        .orderByAsc(SysRole::getSort).orderByAsc(SysRole::getId))
                .stream().map(RoleVO::from).toList();
    }

    @Override
    public RoleVO getDetail(Long id) {
        return RoleVO.from(requireRole(id));
    }

    @Override
    public Long create(RoleForm form) {
        assertCodeUnused(form.getCode(), null);
        SysRole role = new SysRole();
        copyForm(form, role);
        try {
            roleMapper.insert(role);
        } catch (DuplicateKeyException e) {
            throw BusinessException.ofKey("erp.system.role-code-exists", form.getCode());
        }
        return role.getId();
    }

    @Override
    public void update(RoleForm form) {
        SysRole exists = requireRole(form.getId());
        if (SUPER_ADMIN_CODE.equals(exists.getCode()) && !SUPER_ADMIN_CODE.equals(form.getCode())) {
            throw BusinessException.ofKey("erp.system.builtin-role-code-readonly");
        }
        assertCodeUnused(form.getCode(), form.getId());
        SysRole role = new SysRole();
        copyForm(form, role);
        role.setId(form.getId());
        roleMapper.updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysRole role = requireRole(id);
        if (SUPER_ADMIN_CODE.equals(role.getCode())) {
            throw BusinessException.ofKey("erp.system.builtin-role-no-delete");
        }
        List<Long> userIds = userRoleMapper.selectList(Wrappers.lambdaQuery(SysUserRole.class)
                        .eq(SysUserRole::getRoleId, id))
                .stream().map(SysUserRole::getUserId).toList();
        if (!userIds.isEmpty()) {
            Long userCount = userMapper.selectCount(Wrappers.lambdaQuery(SysUser.class)
                    .in(SysUser::getId, userIds));
            if (userCount != null && userCount > 0) {
                throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.system.role-assigned-to-users");
            }
        }
        roleMapper.deleteById(id);
        roleMenuMapper.deletePhysicallyByRoleId(id);
        userRoleMapper.delete(Wrappers.lambdaQuery(SysUserRole.class).eq(SysUserRole::getRoleId, id));
    }

    @Override
    public List<Long> getMenuIds(Long roleId) {
        requireRole(roleId);
        return roleMenuMapper.selectList(Wrappers.lambdaQuery(SysRoleMenu.class)
                        .eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        requireRole(roleId);
        List<Long> distinct = menuIds == null ? List.of() : menuIds.stream().distinct().toList();
        if (!distinct.isEmpty()) {
            Long menuCount = menuMapper.selectCount(Wrappers.lambdaQuery(SysMenu.class)
                    .in(SysMenu::getId, distinct));
            if (menuCount != distinct.size()) {
                throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.system.invalid-menus");
            }
        }
        // 整体覆盖：先物理删旧关联，再批量插入新关联
        roleMenuMapper.deletePhysicallyByRoleId(roleId);
        if (!distinct.isEmpty()) {
            Long currentUserId = SecurityUtils.getCurrentUserIdOrDefault(0L);
            for (Long menuId : distinct) {
                SysRoleMenu roleMenu = new SysRoleMenu();
                roleMenu.setRoleId(roleId);
                roleMenu.setMenuId(menuId);
                roleMenu.setCreateBy(currentUserId);
                roleMenuMapper.insert(roleMenu);
            }
        }
    }

    /** 校验角色编码未被占用（排除自身） */
    private void assertCodeUnused(String code, Long excludeId) {
        Long count = roleMapper.selectCount(Wrappers.lambdaQuery(SysRole.class)
                .eq(SysRole::getCode, code)
                .ne(excludeId != null, SysRole::getId, excludeId));
        if (count != null && count > 0) {
            throw BusinessException.ofKey("erp.system.role-code-exists", code);
        }
    }

    private void copyForm(RoleForm form, SysRole role) {
        role.setName(form.getName());
        role.setCode(form.getCode());
        role.setSort(form.getSort() == null ? 0 : form.getSort());
        role.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        role.setRemark(form.getRemark());
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.system.role-not-exists");
        }
        return role;
    }
}
