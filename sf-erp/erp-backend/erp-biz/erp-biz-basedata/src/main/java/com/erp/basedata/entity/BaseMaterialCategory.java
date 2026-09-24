package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 物料分类实体（表 base_material_category，树形结构）。
 * <p>
 * parentId=0 表示根节点；分类被物料引用或存在子分类时禁止删除（DATA_IN_USE）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_material_category")
public class BaseMaterialCategory extends BaseEntity {

    /** 父分类 ID，0=根节点 */
    private Long parentId;

    /** 分类编码，全局唯一 */
    private String code;

    /** 分类名称 */
    private String name;

    /** 排序号，越小越靠前 */
    private Integer sort;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
