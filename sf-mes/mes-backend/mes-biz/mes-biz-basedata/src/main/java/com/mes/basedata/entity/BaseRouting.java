package com.mes.basedata.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工艺路线头（表 base_routing）。
 * <p>工单下达时取 ACTIVE 版本生成工单工序任务。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("base_routing")
public class BaseRouting extends BaseEntity {
    /** 成品物料 ID（base_material.id） */
    private Long materialId;
    /** 工艺路线版本号 */
    private String version;
    /** 状态：DRAFT 草稿 / ACTIVE 已审核 / DISABLED 停用 */
    private String status;
    /** 备注 */
    private String remark;
}
