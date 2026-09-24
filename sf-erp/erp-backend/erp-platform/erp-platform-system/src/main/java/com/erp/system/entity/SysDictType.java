package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型实体（表 sys_dict_type）。
 * <p>
 * 一类业务枚举（如"通用状态"）对应一条类型记录，编码全局唯一；
 * 具体选项见 {@link SysDictData}（以 type 编码字符串关联，便于其他模块直接取用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity {

    /** 字典名称（展示用，如"通用状态"） */
    private String name;

    /** 类型编码，全局唯一（如 sys_common_status） */
    private String type;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
