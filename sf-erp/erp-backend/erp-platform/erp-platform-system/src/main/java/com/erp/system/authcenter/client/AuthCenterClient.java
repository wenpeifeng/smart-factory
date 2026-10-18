package com.erp.system.authcenter.client;

import com.erp.system.authcenter.dto.AuthCenterUserDTO;

import java.util.List;

/**
 * 统一认证中心服务间客户端契约（sf-auth docs/业务系统接入指南.md §3）。
 * <p>认证：client_credentials 换 service token（Basic erp-web:secret），
 * 实现内部以 Redis 缓存至到期前 60 秒。
 */
public interface AuthCenterClient {

    /**
     * 拉取已分配到本应用（erp-web）的用户（对账式）。
     *
     * @param updatedSince 增量水位（ISO-8601，null=全量）
     * @return 用户列表；active=false 表示本地应软禁用
     */
    List<AuthCenterUserDTO> fetchUsers(String updatedSince);

    /**
     * 应用级禁用用户（仅影响本应用；离职/全局停用工号中心侧拒绝）。
     *
     * @param employeeNo 工号
     */
    void disableUser(String employeeNo);

    /**
     * 应用级启用用户。
     *
     * @param employeeNo 工号
     */
    void enableUser(String employeeNo);

    /** 回写认证中心基础档案。 */
    void updateProfile(String employeeNo, ProfileForm form);

    /** 重置认证中心统一账号密码。 */
    void resetPassword(String employeeNo, String password);

    record ProfileForm(String realName, String position, String email, String phone) { }
}
