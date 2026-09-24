package com.mes.quality.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 检验模板创建/修改表单（头 + 项目行）。
 */
@Data
public class QcTemplateForm {
    /** 模板编码（空=系统生成） */
    private String templateCode;
    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    private String templateName;
    /** 适用物料类别 ID（空=通用） */
    private Long categoryId;
    /** 检验类型：IQC/IPQC/FQC */
    @NotBlank(message = "检验类型不能为空")
    private String inspectType;
    /** 备注 */
    private String remark;
    /** 项目行 */
    @NotEmpty(message = "检验项目不能为空")
    @Valid
    private List<Item> items;

    /** 模板项目行表单 */
    @Data
    public static class Item {
        /** 项目名称 */
        @NotBlank(message = "项目名称不能为空")
        private String itemName;
        /** 标准值（如 10.00±0.10） */
        private String standardValue;
        /** 下限 */
        private BigDecimal lowerLimit;
        /** 上限 */
        private BigDecimal upperLimit;
        /** 检验方法 */
        private String inspectMethod;
        /** 抽检比例 % */
        private BigDecimal sampleRate;
        /** 排序号 */
        private Integer seq;
        /** 备注 */
        private String remark;
    }
}
