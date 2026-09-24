package com.erp.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会计科目表只读映射（表 fin_subject，erp-finance 为表 owner）。
 * <p>
 * 资产类别科目映射校验 / 折旧与处置凭证的科目解析均直接 SELECT 本表
 * （末级判定 = 无下级科目），不 import erp-finance 代码（并行协作约定）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_subject")
public class FinSubject extends BaseEntity {

    /** 科目编码，全局唯一（1601 固定资产 / 1602 累计折旧 / 1606 固定资产清理 等） */
    private String code;

    /** 科目名称 */
    private String name;

    /** 上级科目 ID，0 表示一级科目 */
    private Long parentId;

    /** 科目类别：ASSET-资产 LIABILITY-负债 EQUITY-权益 COST-成本 PROFIT_LOSS-损益 */
    private String category;

    /** 余额方向：DEBIT-借方 CREDIT-贷方 */
    private String direction;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
