package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 工艺路线树形查看 VO —— 工艺路线为线性工序序列，
 * "树"即按序号升序的工序步骤序列（含工序/工作中心回显与生效工时）。
 */
@Data
public class RoutingTreeVO implements Serializable {

    /** 工艺路线ID */
    private Long routingId;

    /** 成品物料ID */
    private Long materialId;

    /** 成品物料编码 */
    private String materialCode;

    /** 成品物料名称 */
    private String materialName;

    /** 版本号 */
    private String version;

    /** 状态 */
    private String status;

    /** 按序号升序的工序序列 */
    private List<StepNode> steps = new ArrayList<>();

    /** 序列节点 */
    @Data
    public static class StepNode implements Serializable {

        /** 序号 */
        private Integer seqNo;

        /** 工序编码 */
        private String operationCode;

        /** 工序名称 */
        private String operationName;

        /** 工作中心编码 */
        private String workCenterCode;

        /** 工作中心名称 */
        private String workCenterName;

        /** 生效单件工时（分钟/件） */
        private BigDecimal effectiveUnitMinutes;

        /** 是否报工点 */
        private Integer reportFlag;
    }
}
