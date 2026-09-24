package com.erp.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * ERP 工艺路线下推 DTO（ERP -> MES 出站契约）。
 * <p>与 MES 侧 {@code com.mes.integration.erp.dto.ErpRoutingDTO} 逐字段对齐。
 */
@Data
public class MesRoutingDTO {
    /** 工艺路线编码 */
    @NotBlank(message = "工艺路线编码不能为空")
    private String routingCode;
    /** 工艺路线名称 */
    @NotBlank(message = "工艺路线名称不能为空")
    private String routingName;
    /** 绑定成品物料编码（推荐提供：工单下达按物料取有效工艺路线） */
    private String materialCode;
    /** 工序步骤 */
    @NotEmpty(message = "工序步骤不能为空")
    private List<Step> steps;

    /** 工艺路线工序步骤 */
    @Data
    public static class Step {
        /** 工序顺序号（1 开始递增） */
        @NotNull(message = "工序顺序号不能为空")
        private Integer seqNo;
        /** 工序编码（不存在则按名称自动建档） */
        @NotBlank(message = "工序编码不能为空")
        private String operationCode;
        /** 工序名称 */
        @NotBlank(message = "工序名称不能为空")
        private String operationName;
        /** 工作中心编码 */
        private String workcenterCode;
        /** 单件工时（分钟） */
        private BigDecimal unitMinutes;
        /** 准备工时（分钟） */
        private Integer setupMinutes;
        /** 是否质检点：1 是 / 0 否 */
        private Integer qcFlag;
    }
}
