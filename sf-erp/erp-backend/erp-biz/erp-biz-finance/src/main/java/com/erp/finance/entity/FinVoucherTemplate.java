package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 凭证模板实体（表 fin_voucher_template，V521）。
 * <p>
 * 业务单据 → 会计凭证的分录规则：{@link #entryRules} 为 JSON 数组，
 * 每个元素 {side, subjectCode, amountExpr, summary}；摘要支持变量替换
 * （{单号} / {日期} / {类型}）。subjectCode 可用特殊值 @account，
 * 由引擎按单据资金账户类型解析（CASH→1001 / BANK→1002）。
 * <p>
 * uk(biz_type)：每类单据一条模板；enabled=0 停用后扫描器不再按该类型生成。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_voucher_template")
public class FinVoucherTemplate extends BaseEntity {

    /** 模板编码（如 TPL-PUR-INSTOCK） */
    private String code;

    /** 模板名称 */
    private String name;

    /** 单据类型：PUR_INSTOCK/SAL_OUTSTOCK/SAL_RET/PUR_RET/INVOICE_SALES/INVOICE_PURCHASE/RECEIPT/PAYMENT */
    private String bizType;

    /** 分录规则 JSON 数组：[{"side":"DEBIT|CREDIT","subjectCode":"1405|@account","amountExpr":"...","summary":"... {单号} ..."}] */
    private String entryRules;

    /** 启用：1 启用 0 停用 */
    private Integer enabled;

    /** 优先级（预留，当前 uk(biz_type) 单模板） */
    private Integer priority;

    /** 备注 */
    private String remark;
}
