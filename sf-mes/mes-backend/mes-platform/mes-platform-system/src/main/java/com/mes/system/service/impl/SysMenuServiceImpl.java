package com.mes.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.system.dto.menu.MenuForm;
import com.mes.system.dto.menu.MenuVO;
import com.mes.system.entity.SysMenu;
import com.mes.system.entity.SysRole;
import com.mes.system.entity.SysRoleMenu;
import com.mes.system.entity.SysUserRole;
import com.mes.system.mapper.SysMenuMapper;
import com.mes.system.mapper.SysRoleMapper;
import com.mes.system.mapper.SysRoleMenuMapper;
import com.mes.system.mapper.SysUserRoleMapper;
import com.mes.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 菜单管理服务实现（树形三级：目录/菜单/按钮）。
 * <p>
 * 树组装在内存完成（菜单为小表全量加载）；内置超管直接返回全部启用菜单，
 * 普通用户走 user_role -> role_menu 联表聚合。
 */
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl implements SysMenuService {

    /** 菜单类型：目录 */
    public static final int TYPE_DIR = 1;
    /** 菜单类型：菜单（路由页） */
    public static final int TYPE_MENU = 2;
    /** 菜单类型：按钮 */
    public static final int TYPE_BUTTON = 3;

    private final SysMenuMapper menuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    @Override
    public List<MenuVO> tree(String keyword) {
        List<SysMenu> menus = menuMapper.selectList(Wrappers.lambdaQuery(SysMenu.class)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysMenu::getName, keyword.trim())
                        .or().like(SysMenu::getPerms, keyword.trim()))
                .orderByAsc(SysMenu::getSort).orderByAsc(SysMenu::getId));
        // 关键词过滤时保留命中节点的祖先链，否则树断裂无法定位
        if (keyword != null && !keyword.isBlank()) {
            menus = keepWithAncestors(menus);
        }
        return buildTree(menus.stream().map(MenuVO::from).toList());
    }

    @Override
    public MenuVO getDetail(Long id) {
        return MenuVO.from(requireMenu(id));
    }

    @Override
    public Long create(MenuForm form) {
        validateForm(form, null);
        SysMenu menu = new SysMenu();
        copyForm(form, menu);
        menuMapper.insert(menu);
        return menu.getId();
    }

    @Override
    public void update(MenuForm form) {
        requireMenu(form.getId());
        validateForm(form, form.getId());
        SysMenu menu = new SysMenu();
        copyForm(form, menu);
        menu.setId(form.getId());
        menuMapper.updateById(menu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireMenu(id);
        Long childCount = menuMapper.selectCount(Wrappers.lambdaQuery(SysMenu.class)
                .eq(SysMenu::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(ErrorCode.DATA_IN_USE, "存在下级菜单/按钮，请先删除子节点");
        }
        menuMapper.deleteById(id);
        // 菜单删除后同步清理角色关联（物理删除，关系数据无保留价值）
        roleMenuMapper.delete(Wrappers.lambdaQuery(SysRoleMenu.class).eq(SysRoleMenu::getMenuId, id));
    }

    @Override
    public List<MenuVO> buildUserMenuTree(Long userId) {
        List<SysMenu> menus;
        if (isSuperAdmin(userId)) {
            // 内置超管：全部启用菜单
            menus = menuMapper.selectList(Wrappers.lambdaQuery(SysMenu.class)
                    .in(SysMenu::getType, List.of(TYPE_DIR, TYPE_MENU))
                    .eq(SysMenu::getStatus, 1)
                    .orderByAsc(SysMenu::getSort).orderByAsc(SysMenu::getId));
        } else {
            // 普通用户：按角色聚合（XML 联表，含角色/菜单启用过滤）
            menus = menuMapper.selectMenusByUserId(userId, List.of(TYPE_DIR, TYPE_MENU));
        }
        return buildTree(menus.stream().map(MenuVO::from).toList());
    }

    @Override
    public List<String> getPermsByUserId(Long userId) {
        // 权限码同时挂在菜单型（:list 查询权）与按钮型（:add/:edit 等操作权）节点上，
        // 两者都收集 —— 与前端按钮显隐（HasPerm）和接口注解（@RequirePerm）口径一致
        List<SysMenu> buttons = isSuperAdmin(userId)
                ? menuMapper.selectList(Wrappers.lambdaQuery(SysMenu.class)
                        .in(SysMenu::getType, List.of(TYPE_MENU, TYPE_BUTTON))
                        .eq(SysMenu::getStatus, 1))
                : menuMapper.selectMenusByUserId(userId, List.of(TYPE_MENU, TYPE_BUTTON));
        return buttons.stream()
                .map(SysMenu::getPerms)
                .filter(perms -> perms != null && !perms.isBlank())
                .distinct()
                .toList();
    }

    /** 用户是否持有内置超管角色（角色须启用） */
    private boolean isSuperAdmin(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(Wrappers.lambdaQuery(SysUserRole.class)
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return false;
        }
        return roleMapper.selectList(Wrappers.lambdaQuery(SysRole.class)
                        .in(SysRole::getId, roleIds)
                        .eq(SysRole::getCode, SysRoleServiceImpl.SUPER_ADMIN_CODE)
                        .eq(SysRole::getStatus, 1))
                .stream().anyMatch(role -> SysRoleServiceImpl.SUPER_ADMIN_CODE.equals(role.getCode()));
    }

    /**
     * 表单组合校验：上级存在性（含防环）、按类型校验 path/component/perms。
     *
     * @param form      菜单表单
     * @param selfId    修改时的自身 ID（新增传 null）
     */
    private void validateForm(MenuForm form, Long selfId) {
        Long parentId = form.getParentId();
        if (parentId != 0) {
            SysMenu parent = menuMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "上级菜单不存在");
            }
            // 按钮只能挂在菜单下；目录/菜单可挂目录或根
            if (form.getType() == TYPE_BUTTON && parent.getType() != TYPE_MENU) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "按钮只能挂在菜单节点下");
            }
            if (form.getType() != TYPE_BUTTON && parent.getType() == TYPE_MENU) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "目录/菜单只能挂在目录或根节点下");
            }
        }
        if (selfId != null && parentId != 0) {
            // 防环：新的上级不能是自己或自己的后代
            if (parentId.equals(selfId) || collectDescendantIds(selfId).contains(parentId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "上级菜单不能选择自己或自己的下级");
            }
        }
        int type = form.getType();
        if (type == TYPE_DIR || type == TYPE_MENU) {
            if (isBlank(form.getPath())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, type == TYPE_DIR ? "目录必须填写路由地址" : "菜单必须填写路由地址");
            }
            if (form.getPath().startsWith("/")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "路由地址不要以斜杠开头（如 system/users）");
            }
        }
        if (type == TYPE_MENU && isBlank(form.getComponent())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "菜单必须填写前端组件路径（如 system/users/UserList）");
        }
        if (type == TYPE_BUTTON && isBlank(form.getPerms())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "按钮必须填写权限标识（如 system:user:add）");
        }
    }

    private void copyForm(MenuForm form, SysMenu menu) {
        menu.setParentId(form.getParentId());
        menu.setName(form.getName());
        menu.setType(form.getType());
        menu.setPath(emptyToNull(form.getPath()));
        menu.setComponent(emptyToNull(form.getComponent()));
        menu.setPerms(emptyToNull(form.getPerms()));
        menu.setIcon(emptyToNull(form.getIcon()));
        menu.setSort(form.getSort() == null ? 0 : form.getSort());
        menu.setVisible(form.getVisible() == null ? 1 : form.getVisible());
        menu.setStatus(form.getStatus() == null ? 1 : form.getStatus());
    }

    private SysMenu requireMenu(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "菜单不存在或已被删除");
        }
        return menu;
    }

    /** BFS 收集自身及全部后代 ID（防环校验用） */
    private Set<Long> collectDescendantIds(Long rootId) {
        List<SysMenu> all = menuMapper.selectList(Wrappers.lambdaQuery(SysMenu.class));
        Map<Long, List<Long>> childrenMap = all.stream()
                .collect(Collectors.groupingBy(SysMenu::getParentId,
                        Collectors.mapping(SysMenu::getId, Collectors.toList())));
        Set<Long> result = new HashSet<>();
        var queue = new ArrayDeque<>(childrenMap.getOrDefault(rootId, List.of()));
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (result.add(current)) {
                queue.addAll(childrenMap.getOrDefault(current, List.of()));
            }
        }
        return result;
    }

    /** 保留命中节点及其祖先链（关键词搜索时不让树断裂） */
    private List<SysMenu> keepWithAncestors(List<SysMenu> hit) {
        Map<Long, SysMenu> all = menuMapper.selectList(Wrappers.lambdaQuery(SysMenu.class))
                .stream().collect(Collectors.toMap(SysMenu::getId, Function.identity()));
        Set<Long> keep = new HashSet<>();
        for (SysMenu menu : hit) {
            Long cursor = menu.getId();
            // 沿 parent 链上溯，最多 all.size() 步防脏数据成环
            int guard = all.size();
            while (cursor != null && cursor != 0 && keep.add(cursor) && guard-- > 0) {
                SysMenu node = all.get(cursor);
                cursor = node == null ? null : node.getParentId();
            }
        }
        return all.values().stream().filter(m -> keep.contains(m.getId())).toList();
    }

    /** 列表组树：parentId 归组后挂到父节点 children */
    private List<MenuVO> buildTree(List<MenuVO> nodes) {
        Map<Long, List<MenuVO>> byParent = nodes.stream()
                .collect(Collectors.groupingBy(MenuVO::getParentId));
        List<MenuVO> roots = new ArrayList<>(byParent.getOrDefault(0L, List.of()));
        for (MenuVO node : nodes) {
            node.setChildren(new ArrayList<>(byParent.getOrDefault(node.getId(), List.of())));
        }
        return roots;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
