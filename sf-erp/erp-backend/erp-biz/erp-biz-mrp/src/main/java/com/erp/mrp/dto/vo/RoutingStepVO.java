package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工艺路线步骤 VO（含工序/工作中心回显；effective 字段为覆盖默认后的生效值）。
 */
@Data
public class RoutingStepVO implements Serializable {

    /** 步骤ID */
    private Long id;

    /** 序号 */
    private Integer seqNo;

    /** 工序ID */
    private Long operationId;

    /** 工序编码 */
    private String operationCode;

    /** 工序名称 */
    private String operationName;

    /** 外协标志（取工序） */
    private Integer outsourceFlag;

    /** 工作中心ID（覆盖后的生效值） */
    private Long workCenterId;

    /** 工作中心编码 */
    private String workCenterCode;

    /** 工作中心名称 */
    private String workCenterName;

    /** 步骤单件工时（未覆盖时为空） */
    private BigDecimal unitMinutes;

    /** 生效单件工时 = 步骤覆盖 ?: 工序默认 */
    private BigDecimal effectiveUnitMinutes;

    /** 是否报工点：1是 0否 */
    private Integer reportFlag;

    /** 备注 */
    private String remark;
}
