package com.mes.integration.authcenter.dto;

import lombok.Data;

/**
 * 用户同步结果报告（对账式同步的返回）。
 */
@Data
public class UserSyncResultVO {
    /** 本次拉取到的用户数 */
    private int fetched;
    /** 新建本地副本数 */
    private int inserted;
    /** 更新本地副本数 */
    private int updated;
    /** 禁用（active=false 或应用进门权被回收）数 */
    private int disabled;
}
