package com.erp.system.service;

import com.erp.common.result.PageResult;
import com.erp.system.dto.role.RoleForm;
import com.erp.system.dto.role.RoleQuery;
import com.erp.system.dto.role.RoleVO;

import java.util.List;

/**
 * 角色管理服务。
 */
public interface SysRoleService {

    /**
     * 分页查询角色。
     *
     * @param query 查询条件
     * @return 角色 VO 分页
     */
    PageResult<RoleVO> page(RoleQuery query);

    /**
     * 全量角色列表（用户分配角色的下拉数据）。
     *
     * @return 角色 VO 列表（按 sort 排序）
     */
    List<RoleVO> listAll();

    /**
     * 角色详情。
     *
     * @param id 角色 ID
     * @return 角色 VO
     */
    RoleVO getDetail(Long id);

    /**
     * 新增角色（编码唯一）。
     *
     * @param form 角色表单
     * @return 新角色 ID
     */
    Long create(RoleForm form);

    /**
     * 修改角色（内置超管 super_admin 编码不可改）。
     *
     * @param form 角色表单（含 id）
     */
    void update(RoleForm form);

    /**
     * 删除角色（内置超管不可删；仍被用户引用时拒绝）。
     *
     * @param id 角色 ID
     */
    void delete(Long id);

    /**
     * 查询角色已分配的菜单 ID 列表。
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 列表
     */
    List<Long> getMenuIds(Long roleId);

    /**
     * 为角色分配菜单（整体覆盖，含按钮）。
     *
     * @param roleId  角色 ID
     * @param menuIds 菜单 ID 列表（空列表清空）
     */
    void assignMenus(Long roleId, List<Long> menuIds);
}
