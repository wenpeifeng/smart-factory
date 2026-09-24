package com.mes.system.service;

import com.mes.system.dto.menu.MenuForm;
import com.mes.system.dto.menu.MenuVO;

import java.util.List;

/**
 * 菜单管理服务（树形）。
 */
public interface SysMenuService {

    /**
     * 全量菜单树（管理页，含目录/菜单/按钮全部三级）。
     *
     * @param keyword 名称/权限标识模糊过滤（可选）
     * @return 菜单树
     */
    List<MenuVO> tree(String keyword);

    /**
     * 菜单详情。
     *
     * @param id 菜单 ID
     * @return 菜单 VO
     */
    MenuVO getDetail(Long id);

    /**
     * 新增菜单（按类型校验 path/component/perms 必填组合）。
     *
     * @param form 菜单表单
     * @return 新菜单 ID
     */
    Long create(MenuForm form);

    /**
     * 修改菜单（上级不允许选自己或自己的后代）。
     *
     * @param form 菜单表单（含 id）
     */
    void update(MenuForm form);

    /**
     * 删除菜单（存在下级或被角色引用时拒绝；允许删除时清理角色关联）。
     *
     * @param id 菜单 ID
     */
    void delete(Long id);

    /**
     * 构建当前用户可见的路由菜单树（目录+菜单，按钮不参与路由）。
     *
     * @param userId 用户 ID
     * @return 菜单树（visible=0 的隐藏菜单仍返回，由前端按需处理）
     */
    List<MenuVO> buildUserMenuTree(Long userId);

    /**
     * 查询用户按钮权限标识列表。
     *
     * @param userId 用户 ID
     * @return 权限标识列表（如 system:user:add）
     */
    List<String> getPermsByUserId(Long userId);
}
