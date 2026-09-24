package com.erp.report.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单据联查链节点 VO（跨模块只读）。
 * <p>
 * 各表字段经 SQL 别名统一为 docId/docNo/bizDate/amount/status；
 * docType/docName/reversed 由 Service 层补充。
 */
@Data
public class DocChainNodeVO {

    /** 单据类型（DocTypes 常量） */
    private String docType;

    /** 单据 ID（对应表主键） */
    private Long docId;

    /** 单号 */
    private String docNo;

    /** 单据类型中文名（Service 回填） */
    private String docName;

    /** 业务日期 */
    private LocalDate bizDate;

    /** 金额（凭证为借方合计；报价无金额为 null） */
    private BigDecimal amount;

    /** 原始状态值（各表口径不一，前端按 docType 映射展示） */
    private String status;

    /** 是否已被红冲（biz_reverse_log 存在以本单为来源的记录） */
    private Boolean reversed;
}
