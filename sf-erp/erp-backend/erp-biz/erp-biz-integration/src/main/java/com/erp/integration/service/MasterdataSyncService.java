package com.erp.integration.service;

/**
 * 主数据对账推送服务：物料 / BOM / 工艺路线全量下推 MES（契约接口 #1）。
 * <p>触发：每日定时全量对账（MasterdataReconcileJob）+ 管理端手动触发。
 * MES 侧按 code / materialCode+version / routingCode 幂等 upsert，
 * 全量重推即对账补偿（差异随全量覆盖收敛）。
 */
public interface MasterdataSyncService {

    /**
     * 全量推送全部主数据（物料 + 有效 BOM + 有效工艺路线）。
     *
     * @return 推送摘要（各类型入队条数）
     */
    String reconcileAll();

    /** 物料全量推送条数（入 outbox） */
    int pushAllMaterials();

    /** 有效 BOM（status=ACTIVE）全量推送条数 */
    int pushAllBoms();

    /** 有效工艺路线（status=ACTIVE）全量推送条数 */
    int pushAllRoutings();
}
