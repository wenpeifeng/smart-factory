package com.erp.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 组织间调拨价目（org_transfer_price）。
 * <p>
 * 定价线路 = 物料 × 调出工厂 × 调入工厂（唯一）；
 * 内部结算价 = 成本单价 ×(1 + RATE%) 或 成本单价 + FIXED 固定额。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("org_transfer_price")
public class OrgTransferPrice extends BaseEntity {

    /** 物料 ID（base_material.id，跨模块只读引用） */
    private Long materialId;

    /** 调出工厂 ID（org_factory.id） */
    private Long fromFactoryId;

    /** 调入工厂 ID（org_factory.id） */
    private Long toFactoryId;

    /** 加价类型：RATE 加价率% / FIXED 固定加价额 */
    private String markupType;

    /** 加价值：RATE=百分数(10 代表 10%)；FIXED=每单位加价金额 */
    private BigDecimal markupValue;

    /** 状态：1 启用 0 停用（停用价目不参与结算价计算） */
    private Integer status;

    /** 备注 */
    private String remark;
}
