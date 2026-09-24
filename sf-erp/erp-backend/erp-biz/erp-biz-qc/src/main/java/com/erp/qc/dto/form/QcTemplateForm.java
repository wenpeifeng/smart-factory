package com.erp.qc.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 检验模板新增/修改表单（明细整体覆盖式保存）。
 */
@Data
public class QcTemplateForm {

    /** 模板编码（唯一；修改时不可改码，服务层校验） */
    @NotBlank(message = "模板编码不能为空")
    @Size(max = 40, message = "模板编码最长 40 字")
    private String templateCode;

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称最长 100 字")
    private String templateName;

    /** 适用物料分类 ID（空=全部分类通用） */
    private Long categoryId;

    /** 检验类型 */
    @NotBlank(message = "检验类型不能为空")
    @Pattern(regexp = "IQC|IPQC|FQC|OQC", message = "检验类型只允许 IQC/IPQC/FQC/OQC")
    private String inspectType;

    /** 状态：1启用 0停用 */
    @NotNull(message = "状态不能为空")
    private Integer status;

    /** 备注 */
    @Size(max = 500, message = "备注最长 500 字")
    private String remark;

    /** 检验项目（至少一行） */
    @NotEmpty(message = "检验项目不能为空")
    @Valid
    private List<TemplateItemForm> items;

    /**
     * 检验项目行（模板行=检验项目）。
     */
    @Data
    public static class TemplateItemForm {

        /** 检验项目名称 */
        @NotBlank(message = "检验项目名称不能为空")
        @Size(max = 100, message = "检验项目名称最长 100 字")
        private String itemName;

        /** 标准值（文本/数值均可） */
        @Size(max = 100, message = "标准值最长 100 字")
        private String standardValue;

        /** 标准下限（空=不限） */
        @DecimalMin(value = "0", message = "下限不能为负数")
        private BigDecimal lowerLimit;

        /** 标准上限（空=不限） */
        @DecimalMin(value = "0", message = "上限不能为负数")
        private BigDecimal upperLimit;

        /** 检验方法 */
        @Size(max = 100, message = "检验方法最长 100 字")
        private String inspectMethod;

        /** 抽检比例 %（0-100） */
        @NotNull(message = "抽检比例不能为空")
        @DecimalMin(value = "0", message = "抽检比例不能为负数")
        @DecimalMax(value = "100", message = "抽检比例不能超过 100")
        private BigDecimal sampleRate;

        /** 排序（小在前） */
        private Integer seq;

        /** 行备注 */
        @Size(max = 200, message = "行备注最长 200 字")
        private String remark;
    }
}
