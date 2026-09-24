/**
 * 基础资料模块（物料/物料分类/计量单位/客户/供应商/仓库/库位/结算方式）。
 * <p>
 * 分层：controller(接口) / service+impl(业务) / mapper(ORM) / entity(表映射) / dto(form入参-vo出参-query查询)。
 * 被上游模块（采购/销售/库存/MRP/财务）共享，跨模块只允许调用本模块 Service 接口，
 * 对外能力清单见 docs/modules/basedata.md。
 */
package com.erp.basedata;
