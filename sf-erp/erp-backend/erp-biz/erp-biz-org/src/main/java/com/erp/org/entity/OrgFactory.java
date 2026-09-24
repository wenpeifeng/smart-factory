package com.erp.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工厂档案（org_factory）。
 * <p>
 * 轻量多组织模型：工厂是组织维度的最小单元，仓库通过
 * base_warehouse.factory_id 挂到工厂（归属调整见 /org/factories/assign-warehouse）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("org_factory")
public class OrgFactory extends BaseEntity {

    /** 工厂编码（唯一，自动生成 FC+yyyyMMdd+3位流水） */
    private String code;

    /** 工厂名称 */
    private String name;

    /** 工厂地址 */
    private String address;

    /** 负责人 */
    private String manager;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
