/**
 * ERP↔MES 集成模块（ERP 侧实现）。
 * <p>契约唯一事实源：{@code sf-mes/docs/MES与ERP协同设计.md}，六类接口：
 * <ul>
 *   <li>下推（ERP→MES）：主数据（物料/BOM/工艺路线）+ 生产订单（下达事件触发）；</li>
 *   <li>回传（MES→ERP）：完工报工 / 领料 / 退料 / 质检结果 / 工单进度镜像。</li>
 * </ul>
 * 可靠性：出站走 int_outbox（指数退避 1m/5m/30m/2h/6h，5 次后死信）；
 * 入站按 bizType+bizKey+eventTime 幂等落 int_message_log。
 * DTO 与 MES 侧 {@code com.mes.integration.erp.dto.*} 逐字段对齐，一字不差。
 */
package com.erp.integration;
