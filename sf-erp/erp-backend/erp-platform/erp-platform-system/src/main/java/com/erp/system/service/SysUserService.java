package com.erp.system.service;

import com.erp.common.result.PageResult;
import com.erp.system.dto.user.UserQuery;
import com.erp.system.dto.user.UserScopeForm;
import com.erp.system.dto.user.UserVO;

import java.util.List;

/**
 * 用户管理服务（身份体系已移交统一认证中心，与 MES 同款收敛口径）：
 * 查询 / 数据范围调整 / 角色分配 / 组织绑定。
 * 用户生命周期（建号/改密/删除/启停）不再本系统提供——建档走认证中心
 * 同步或 SSO 首登 JIT，启停走应用级启停（调认证中心，仅影响本系统）。
 */
public interface SysUserService {

    /**
     * 分页查询用户（含部门名与角色聚合）。
     *
     * @param query 查询条件
     * @return 用户 VO 分页
     */
    PageResult<UserVO> page(UserQuery query);

    /**
     * 用户详情（含已分配角色）。
     *
     * @param id 用户 ID
     * @return 用户 VO
     */
    UserVO getDetail(Long id);

    /**
     * 调整数据范围/部门/组织（五档数据权限，本系统内部授权）。
     *
     * @param id   用户 ID
     * @param form 数据范围表单
     */
    void updateScope(Long id, UserScopeForm form);

    /**
     * 分配角色（整体覆盖）。
     *
     * @param id      用户 ID
     * @param roleIds 角色 ID 列表（空列表清空）
     */
    void assignRoles(Long id, List<Long> roleIds);

    /**
     * 查询用户绑定的组织 ID 列表（V1042 sys_user_org，登录可切换组织数据源）。
     *
     * @param id 用户 ID
     * @return 组织 ID 列表（未绑定为空列表 = 不限制，登录可见全部启用组织）
     */
    List<Long> getUserOrgIds(Long id);

    /**
     * 绑定组织（整体覆盖；空列表清空绑定，回退全部启用组织）。
     * <p>
     * 绑定后该用户登录/切换组织的组织列表收敛为"绑定 ∩ 启用"；
     * 已签发的旧令牌不受影响，重新登录或切换组织时生效。
     *
     * @param id     用户 ID
     * @param orgIds 组织 ID 列表（须为启用组织）
     */
    void assignOrgs(Long id, List<Long> orgIds);

    /**
     * 查询用户已绑定的角色编码（含逻辑删除过滤，内置超管恒返回 super_admin）。
     *
     * @param userId 用户 ID
     * @return 角色编码列表
     */
    List<String> getRoleCodesByUserId(Long userId);
}
