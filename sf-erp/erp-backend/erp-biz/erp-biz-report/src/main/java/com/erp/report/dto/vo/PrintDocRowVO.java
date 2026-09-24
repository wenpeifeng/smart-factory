package com.erp.report.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 打印中心「单据选择列表」行 VO（打印中心各 Tab 的可选单据清单）。
 * <p>
 * 5 类单据统一收敛为 编号/往来单位/日期/金额/状态 五列；
 * 对账单（statement）的 id 为客户 ID，billDate 为对账月份 yyyy-MM，
 * billNo 为客户编码。
 */
@Data
public class PrintDocRowVO {

    /** 单据 ID（statement 为客户 ID） */
    private Long id;

    /** 单据编号（statement 为客户编码） */
    private String billNo;

    /** 往来单位名称（客户/供应商；凭证/对账单为空或制单人） */
    private String partyName;

    /** 单据日期 yyyy-MM-dd（statement 为 yyyy-MM） */
    private String billDate;

    /** 单据金额（statement 为当月应收发生额） */
    private BigDecimal amount;

    /** 单据状态中文（草稿/已审核/已过账…） */
    private String statusLabel;

    /** 附加说明列（statement=当月出库单数，其他为空） */
    private String extra;
}
