package com.mes.quality.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 检验模板实体（表 qc_template，org_id=0 共享）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("qc_template")
public class QcTemplate extends BaseEntity {
    /** 模板编码 */
    private String templateCode;
    /** 模板名称 */
    private String templateName;
    /** 适用物料类别 ID（NULL=全部分类通用） */
    private Long categoryId;
    /** 检验类型：IQC 来料 / IPQC 过程 / FQC 成品 */
    private String inspectType;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    /** 备注 */
    private String remark;
}
