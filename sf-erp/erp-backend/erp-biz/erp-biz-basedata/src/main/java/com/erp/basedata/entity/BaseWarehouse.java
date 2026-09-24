package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 仓库实体（表 base_warehouse）。
 * <p>
 * 仓库存在库位或被库存/单据引用后禁止删除；
 * 停用后不允许新单据选择该仓库（历史单据不受影响）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_warehouse")
public class BaseWarehouse extends BaseEntity {

    /** 仓库编码，全局唯一（自动生成规则：CK+日期+3位流水） */
    private String code;

    /** 仓库名称 */
    private String name;

    /** 负责人 */
    private String manager;

    /** 地址 */
    private String address;

    /** 是否启用：1-是 0-否 */
    private Integer status;

    /** 备注 */
    private String remark;
}
