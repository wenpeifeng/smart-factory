/**
 * 销售管理模块（销售订单 / 销售出库 / 销售退货 / 销售统计）。
 * <p>
 * 单据流：销售订单(信用校验审核) → 销售出库(扣库存/写流水/生应收) → 销售退货(回补库存/红字应收)。
 * 分层：controller / service(.impl) / mapper(复杂 SQL 见 resources/mapper/sales/) / entity / dto(form·vo) / constant。
 * 跨模块协作：只按约定表结构读写 base_* / inv_stock / inv_movement / fin_receivable，
 * 不 import 其他业务模块代码（详见 docs/modules/sales.md）。
 */
package com.erp.sales;
