package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门实体（表 sys_dept）。
 * <p>
 * parent_id 自关联构成组织树（0 为根）。sys_user.dept_id 逻辑引用本表，
 * 删除部门前必须校验不存在下级部门与挂靠用户。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseEntity {

    /** 上级部门 ID，0 表示根节点 */
    private Long parentId;

    /** 部门名称 */
    private String name;

    /** 显示顺序，越小越靠前 */
    private Integer sort;

    /** 负责人 */
    private String leader;

    /** 联系电话 */
    private String phone;

    /** 状态：1-启用 0-停用 */
    private Integer status;
}
