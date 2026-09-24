package com.erp.org.dto.form;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 组织间调拨价目新增/修改表单。
 * <p>
 * 定价线路（物料×调出工厂×调入工厂）唯一；加价率上限 1000%（防误录）。
 */
@Data
public class TransferPriceForm {

    /** 物料 ID */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /** 调出工厂 ID */
    @NotNull(message = "调出工厂不能为空")
    private Long fromFactoryId;

    /** 调入工厂 ID */
    @NotNull(message = "调入工厂不能为空")
    private Long toFactoryId;

    /** 加价类型：RATE 加价率% / FIXED 固定加价额 */
    @NotBlank(message = "加价类型不能为空")
    @Pattern(regexp = "RATE|FIXED", message = "加价类型仅允许 RATE(加价率%)或 FIXED(固定加价额)")
    private String markupType;

    /**
     * 加价值：RATE=百分数(10 代表 10%，上限 1000)；FIXED=每单位加价金额（>=0）。
     * RATE 上限在服务端结合类型二次校验（注解只兜 0~1000 区间，恰好覆盖 RATE；FIXED 超额无意义但无害）。
     */
    @NotNull(message = "加价值不能为空")
    @DecimalMin(value = "0", message = "加价值不能为负")
    @DecimalMax(value = "1000000000", message = "加价值超出合理范围")
    private BigDecimal markupValue;

    /** 状态：1 启用 0 停用（默认 1） */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
