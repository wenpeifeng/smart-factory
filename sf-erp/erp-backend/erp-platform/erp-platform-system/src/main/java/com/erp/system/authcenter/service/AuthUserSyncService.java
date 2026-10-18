package com.erp.system.authcenter.service;

import com.erp.system.authcenter.dto.UserSyncResultVO;

/**
 * 认证中心用户同步服务（对账式，SCIM 风格）。
 * <p>认证中心提交后实时回调 + 每分钟定时兜底 + 手动触发。
 */
public interface AuthUserSyncService {

    /**
     * 执行一次对账式同步：client_credentials 取 service token →
     * 拉本应用全量用户 → upsert 本地 sys_user → active=false 软禁用 →
     * 中心已回收分配的本地同步用户（孤儿）禁用。
     *
     * @return 同步结果 {added,updated,disabled,orphanDisabled,total}
     */
    default UserSyncResultVO sync() {
        return sync("GROUP");
    }

    /**
     * @param source GROUP=认证中心/集团操作，LOCAL=本 ERP 管理端回写后对账
     */
    UserSyncResultVO sync(String source);

    /**
     * SSO 登录 JIT 建档：用户已通过认证中心登录（能拿到 code 即已进门），
     * 本地尚无副本时用 claims 直接建档（工号/姓名/随机口令/status=1），
     * roles 含 erp.admin 时授予本地超管——登录链路零依赖定时任务与列表接口。
     *
     * @param employeeNo 工号（匹配键，不可变）
     * @param realName   姓名（claims.name，空则用工号占位）
     * @param adminTag   roles 是否含 erp.admin
     * @return 新建的本地用户
     */
    com.erp.system.entity.SysUser jitCreate(String employeeNo, String realName, boolean adminTag);

    /**
     * 应用级启停（转调认证中心 PUT /service/apps/erp-web/users/{工号}/status，
     * 仅影响本系统进门权），随后本地 status 同步翻转；
     * 无工号的本地降级账号直接本地翻转（记录日志提示）。
     *
     * @param userId  本地用户 ID
     * @param enabled true=启用 false=禁用
     */
    void changeAppStatus(Long userId, boolean enabled);

    /** 回写认证中心基础档案并刷新本地镜像。 */
    void updateProfile(Long userId, ProfileForm form);

    /** 重置统一账号密码。 */
    void resetPassword(Long userId, String password);

    record ProfileForm(String realName, String position, String email, String phone) { }
}
