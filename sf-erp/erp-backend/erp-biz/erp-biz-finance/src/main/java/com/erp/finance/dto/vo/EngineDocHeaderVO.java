package com.erp.finance.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 凭证引擎来源单据头（八类单据只读快照的归一化视图，V521）。
 * <p>
 * record 构造器按列序位置映射（与发票模块 InvQueryMapper 同一约定）。
 *
 * @param id          来源单据 ID
 * @param docNo       单号（入库/出库/退货/发票/收款/付款单号）
 * @param docDate     业务日期（凭证日期）：入库=instock_date / 出库=out_date /
 *                    退货=return_date / 销项发票=invoice_date / 进项发票=优先认证日 /
 *                    收付款=biz_date
 * @param partnerId   往来单位（客户/供应商 ID）
 * @param accountId   资金账户 ID（仅收/付款单非空，用于 @account 科目解析）
 * @param totalAmount 单头金额（价税合计 / 收付款金额）
 * @param taxAmount   税额（仅发票类有：发票表 tax_amount，红字票自带负号；其余 NULL）
 * @param amountExcl  不含税金额（仅发票类有：发票表 amount；其余 NULL）
 * @param status      单据状态（发票红冲=20 等原始值，仅诊断用）
 * @param redFlag     红字标志（仅发票类非 0；红字票金额列已为负数）
 */
public record EngineDocHeaderVO(Long id, String docNo, LocalDate docDate, Long partnerId,
                                Long accountId, BigDecimal totalAmount, BigDecimal taxAmount,
                                BigDecimal amountExcl, Integer status, Integer redFlag) {
}
