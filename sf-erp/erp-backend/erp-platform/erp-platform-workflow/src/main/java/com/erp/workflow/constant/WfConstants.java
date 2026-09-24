package com.erp.workflow.constant;

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
            "mrp_work_order", "fin_payment", "fin_receipt",
            // V131 价格中心：销售特价审批（无启用流程时 startFlow 返回 SKIPPED，转管理员简单审批）
            "sal_price_special",
            // V141 供应商准入：准入申请审批（无启用流程时转管理员简单审批）
            "supplier_admit");

    /** 预警去重 Redis key 前缀：erp:wf:warn:{类型}:{业务键}，TTL 24h */
    public static final String WARN_DEDUP_KEY_PREFIX = "erp:wf:warn:";

    /** 预警接收人：管理员（sys_user.id=1，初始化数据约定） */
    public static final long WARN_RECEIVER_ADMIN = 1L;
}
