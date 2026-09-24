package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会计科目实体（表 fin_subject）。
 * <p>
 * 树形结构：parent_id = 0 表示一级科目；编码沿用企业会计准则
 * （1001 库存现金 / 1122 应收账款 / 2202 应付账款 / 6001 主营业务收入 等）。
 * 凭证分录只能挂<b>末级科目</b>（无下级的科目），与金蝶总账一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_subject")
public class FinSubject extends BaseEntity {

    /** 所属账套 ID（fin_book.id，多账套隔离键：Service 层统一注入查询条件与写入值）*/
    private Long bookId;

    /** 科目编码，全局唯一（含明细级，如 222101） */
    private String code;

    /** 科目名称 */
    private String name;

    /** 上级科目 ID，0 表示一级科目 */
    private Long parentId;

    /** 科目类别：ASSET-资产 LIABILITY-负债 EQUITY-权益 COST-成本 PROFIT_LOSS-损益 */
    private String category;

    /** 余额方向：DEBIT-借方 CREDIT-贷方 */
    private String direction;

    /** 状态：1-启用 0-停用（停用科目不可新增分录） */
    private Integer status;

    /** 备注 */
    private String remark;
}
