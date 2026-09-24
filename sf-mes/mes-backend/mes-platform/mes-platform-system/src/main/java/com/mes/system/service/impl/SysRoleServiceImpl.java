package com.mes.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.system.dto.role.RoleForm;
import com.mes.system.dto.role.RoleQuery;
import com.mes.system.dto.role.RoleVO;
import com.mes.system.entity.SysMenu;
import com.mes.system.entity.SysRole;
import com.mes.system.entity.SysRoleMenu;
import com.mes.system.entity.SysUser;
import com.mes.system.entity.SysUserRole;
import com.mes.system.mapper.SysMenuMapper;
import com.mes.system.mapper.SysRoleMapper;
import com.mes.system.mapper.SysRoleMenuMapper;
import com.mes.system.mapper.SysUserMapper;
import com.mes.system.mapper.SysUserRoleMapper;
import com.mes.system.security.SecurityUtils;
import com.mes.system.service.SysRoleService;
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
            throw new BusinessException("角色编码[%s]已存在", form.getCode());
        }
        return role.getId();
    }

    @Override
    public void update(RoleForm form) {
        SysRole exists = requireRole(form.getId());
        if (SUPER_ADMIN_CODE.equals(exists.getCode()) && !SUPER_ADMIN_CODE.equals(form.getCode())) {
            throw new BusinessException("内置超管角色编码不允许修改");
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
            throw new BusinessException("内置超管角色不允许删除");
        }
        List<Long> userIds = userRoleMapper.selectList(Wrappers.lambdaQuery(SysUserRole.class)
                        .eq(SysUserRole::getRoleId, id))
                .stream().map(SysUserRole::getUserId).toList();
        if (!userIds.isEmpty()) {
            Long userCount = userMapper.selectCount(Wrappers.lambdaQuery(SysUser.class)
                    .in(SysUser::getId, userIds));
            if (userCount != null && userCount > 0) {
                throw new BusinessException(ErrorCode.DATA_IN_USE, "角色已分配给用户，请先解除分配");
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
                throw new BusinessException(ErrorCode.BAD_REQUEST, "存在无效的菜单，请刷新后重试");
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
            throw new BusinessException("角色编码[%s]已存在", code);
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
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "角色不存在或已被删除");
        }
        return role;
    }
}
