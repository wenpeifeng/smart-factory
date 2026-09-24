package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 财务报表取数映射配置（表 fin_report_mapping，V531）。
 * <p>
 * 资产负债表 / 利润表的行项目 ↔ 科目编码映射，报表引擎按本表配置取数，
 * 科目体系变化时只需 UPDATE 配置行，无需改代码。
 * <ul>
 *   <li>report_type：BALANCE_SHEET 资产负债表 / INCOME_STATEMENT 利润表；</li>
 *   <li>subject_codes：逗号分隔科目编码集合，<b>前缀匹配</b>（2221 命中 222101），
 *       空串 = 无对应科目（行显示 0）；</li>
 *   <li>direction：DEBIT 借方净额(借-贷) / CREDIT 贷方净额(贷-借)；</li>
 *   <li>calc_type：ASSET_TOTAL / LIAB_TOTAL / EQUITY_TOTAL / LIAB_EQ_TOTAL /
 *       OPERATING_PROFIT / TOTAL_PROFIT / NET_PROFIT 计算行，空 = 普通取数行；</li>
 *   <li>include_pl：未分配利润行并入当期损益净额（未结转损益），保证勾稽平衡。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_report_mapping")
public class FinReportMapping extends BaseEntity {

    /** 租户 ID（默认 1000，与 V1012 对齐） */
    private Long tenantId;

    /** 组织 ID（0=租户级共享配置） */
    private Long orgId;

    /** 账套 ID（0=全账套通用，映射按科目编码对齐） */
    private Long bookId;

    /** 报表类型：BALANCE_SHEET 资产负债表 / INCOME_STATEMENT 利润表 */
    private String reportType;

    /** 行项目所属侧：ASSET 资产 / LIABILITY 负债 / EQUITY 权益（利润表留空） */
    private String side;

    /** 行项目名称（含「减：」「一、」等展示前缀） */
    private String itemName;

    /** 取数科目编码集合（逗号分隔，前缀匹配，空=无科目行显示 0） */
    private String subjectCodes;

    /** 取数方向：DEBIT 借方净额(借-贷) / CREDIT 贷方净额(贷-借) */
    private String direction;

    /** 计算行类型（空=普通取数行） */
    private String calcType;

    /** 是否并入当期损益净额：1=是（未分配利润行） */
    private Integer includePl;

    /** 行序（小在前） */
    private Integer sortNo;

    /** 备注（口径说明） */
    private String remark;
}
