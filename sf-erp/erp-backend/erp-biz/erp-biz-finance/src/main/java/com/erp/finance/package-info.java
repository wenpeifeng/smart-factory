/**
 * 财务管理（erp-finance）：
 * 应收应付（收/付款核销）+ 资金账户（现金/银行）+ 总账凭证（借贷平衡/过账/科目汇总/试算平衡）。
 *
 * <p>分层约定：controller 参数接收与响应组装 / service 业务与事务 /
 * mapper 数据访问（复杂 SQL 在 resources/mapper/finance/）/ entity 表映射 / dto 三段式（request/form/vo）。
 *
 * <p>并行协作：fin_receivable / fin_payable 由销售/采购模块直接 INSERT 生成，
 * 本模块为表 owner 负责核销回写与查询；客户/供应商名称只读查询
 * base_customer / base_supplier（不 import 对方代码）。
 */
package com.erp.finance;
