package com.mes.integration.service;

import com.mes.integration.authcenter.dto.UserSyncResultVO;

/**
 * auth-center 用户同步服务。
 * <p>将 auth-center 中已分配到本应用的用户对账式同步到 sys_user（本地副本）：
 * 新用户建档（随机初始口令，登录走 SSO）、变更用户更新、active=false 软删除（status=0）。
 */
public interface AuthUserSyncService {

    /**
     * 执行一次同步。
     *
     * @return 同步结果报告
     */
    default UserSyncResultVO sync() {
        return sync("GROUP");
    }

    /**
     * @param source GROUP=认证中心/集团操作，LOCAL=本 MES 管理端回写后对账
     */
    UserSyncResultVO sync(String source);

    /**
     * 单用户建档/刷新（JIT 登录复用）：按工号 upsert + mes.admin 角色映射。
     *
     * @param employeeNo 工号（不可变身份键，落 employee_no 列）
     * @param username   登录名（来自中心 claims，默认=工号）
     * @param nickname   姓名（来自中心 claims）
     * @param mesAdmin   是否持有 mes.admin 标签
     * @return 本地用户（已建档并启用）
     */
    com.mes.system.entity.SysUser provision(String employeeNo, String username, String nickname, boolean mesAdmin);
}
