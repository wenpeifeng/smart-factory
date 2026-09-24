package com.erp.system.authcenter.dto;

import lombok.Data;

/**
 * 认证中心用户同步结果报告（对账式）。
 */
@Data
public class UserSyncResultVO {

    /** 本次拉取到的用户数 */
    private int total;

    /** 新建本地用户数 */
    private int added;

    /** 更新本地用户数 */
    private int updated;

    /** 软禁用数（active=false：LEFT/SUSPENDED/APP_DISABLED） */
    private int disabled;

    /** 孤儿禁用：本地同步副本已被中心回收分配/删除，按对账禁用 */
    private int orphanDisabled;
}
