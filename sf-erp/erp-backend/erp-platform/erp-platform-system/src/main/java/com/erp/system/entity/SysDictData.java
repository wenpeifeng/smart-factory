package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据实体（表 sys_dict_data）。
 * <p>
 * 字典项：label 展示 / value 存储；通过 dict_type 编码挂到
 * {@link SysDictType} 下。其他模块统一走 GET /dict/{type} 拉取。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
public class SysDictData extends BaseEntity {

    /** 所属字典类型编码（sys_dict_type.type） */
    private String dictType;

    /** 选项标签（展示用） */
    private String label;

    /** 选项值（存储用） */
    private String value;

    /** 显示顺序，越小越靠前 */
    private Integer sort;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
