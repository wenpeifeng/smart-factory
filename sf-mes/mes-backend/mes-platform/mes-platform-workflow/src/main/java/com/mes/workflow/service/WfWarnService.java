package com.mes.workflow.service;

/**
 * 预警任务服务 —— 周期扫描库存安全线/客户信用/生产超期并给管理员发 WARN 消息。
 * <p>
 * 扫描口径（跨模块只读，SQL 见 WfRefMapper）：
 * <ul>
 *   <li>库存低于安全库存：base_material.safety_stock vs inv_stock 全仓汇总现存量；</li>
 *   <li>客户信用超限：base_customer.credit_limit vs fin_receivable 非结算单据的 (amount-settled_amount) 合计；</li>
 *   <li>生产订单超期：mrp_work_order 未完工且 plan_end_date &lt; 今天。</li>
 * </ul>
 * 同一预警 24h 内去重：Redis SETNX key=mes:wf:warn:{类型}:{业务键}，TTL 24h。
 */
public interface WfWarnService {

    /**
     * 执行一次全量预警扫描（定时任务与手动触发共用）。
     *
     * @return 本次新发出的预警消息条数（去重后）
     */
    int scanOnce();
}
