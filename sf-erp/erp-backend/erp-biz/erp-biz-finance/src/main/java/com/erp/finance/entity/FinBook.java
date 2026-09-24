package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 账套档案实体（表 fin_book）。
 * <p>
 * 多账套（对标金蝶）：一套账 = 独立的科目树 + 凭证 + 应收应付 + 资金账户；
 * 业务表通过 book_id 关联本表，切换账套走请求头 X-Book-Id（见 BookContext）。
 * 默认账套（is_default=1，种子 1001）不可删除，是无请求头时的缺省账套。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_book")
public class FinBook extends BaseEntity {

    /** 账套编码（BS + 4 位流水，如 BS1001，全局唯一） */
    private String code;

    /** 账套名称（如：主账套 / 测试账套） */
    private String name;

    /** 会计期间政策：NATURAL_MONTH-自然月（当前唯一，预留扩展） */
    private String periodPolicy;

    /** 本位币（ISO 代码，默认 CNY 人民币） */
    private String currency;

    /** 默认账套：1-是（不可删）0-否 */
    private Integer isDefault;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
