package com.mes.integration.authcenter;

import com.mes.integration.authcenter.dto.AuthCenterUserDTO;

import java.util.List;

/**
 * auth-center 服务间客户端契约（docs/业务系统接入指南.md §3）。
 * <p>认证：client_credentials 换 service token（RS256，aud=client_id），
 * http 实现内部缓存；stub 实现记日志返回空。
 */
public interface AuthCenterClient {

    /**
     * 拉取已分配到本应用的用户（对账式；active=false 表示本地应软删除）。
     *
     * @param updatedSince 增量水位（null=全量）
     * @return 用户列表
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

    void updateProfile(String employeeNo, ProfileForm form);

    void resetPassword(String employeeNo, String password);

    record ProfileForm(String realName, String position, String email, String phone) { }
}
