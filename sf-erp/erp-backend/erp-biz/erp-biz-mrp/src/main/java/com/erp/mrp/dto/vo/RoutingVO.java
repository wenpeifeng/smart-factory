package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工艺路线 VO（列表行含步骤数；详情含步骤明细）。
 */
@Data
public class RoutingVO implements Serializable {

    /** 工艺路线ID */
    private Long id;

    /** 成品物料ID */
    private Long materialId;

    /** 成品物料编码 */
    private String materialCode;

    /** 成品物料名称 */
    private String materialName;

    /** 计量单位 */
    private String unit;

    /** 版本号 */
    private String version;

    /** 状态：DRAFT/ACTIVE/DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 步骤数 */
    private Long stepCount;

    /** 步骤明细（详情时返回） */
    private List<RoutingStepVO> steps = new ArrayList<>();
}
