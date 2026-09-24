package com.mes.workflow.constant;

import java.util.Set;

/**
 * 工作流模块常量。
 * <p>
 * 单据类型白名单与字典 wf_biz_type（V901 初始化）保持一致；
 * Redis key 约定集中在本类，避免散落字符串。
 */
public final class WfConstants {

    private WfConstants() {
    }

    /** 支持接入审批流的单据类型（与字典 wf_biz_type 一一对应） */
    public static final Set<String> BIZ_TYPES = Set.of(
            "pur_order", "sal_order", "pur_instock", "sal_outstock",
            "mrp_work_order", "fin_payment", "fin_receipt");

    /** 预警去重 Redis key 前缀：mes:wf:warn:{类型}:{业务键}，TTL 24h */
    public static final String WARN_DEDUP_KEY_PREFIX = "mes:wf:warn:";

    /** 预警接收人：管理员（sys_user.id=1，初始化数据约定） */
    public static final long WARN_RECEIVER_ADMIN = 1L;
}
