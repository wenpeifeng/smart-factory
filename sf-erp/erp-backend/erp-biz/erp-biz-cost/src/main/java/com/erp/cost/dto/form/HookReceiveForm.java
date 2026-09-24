package com.erp.cost.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 业务计价补录表单（POST /cost/hooks/receive）。
 * <p>
 * 用于存量数据初始化或一期单据漏计价的补账：入库方向传正数量 + 金额；
 * 出库方向传正数量，成本按当前移动加权价自动计算。
 */
@Data
public class HookReceiveForm {

    /** 物料 ID */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /** 仓库 ID（可选，仅溯源） */
    private Long warehouseId;

    /** 业务类型编码（CostBizType，如 PUR_IN / SAL_OUT） */
    @NotBlank(message = "业务类型不能为空")
    private String bizType;

    /** 来源单号（幂等溯源用，建议传原单据号） */
    @NotBlank(message = "来源单号不能为空")
    @Size(max = 40, message = "单号长度不能超过40")
    private String bizNo;

    /** 数量：恒为正数，方向由 bizType 决定（入库/出库） */
    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.0001", message = "数量必须大于0")
    private BigDecimal qty;

    /** 入库金额（入库方向可选：空 = 按当前移动加权价估算；出库方向忽略） */
    @DecimalMin(value = "0", message = "金额不能为负")
    private BigDecimal amount;

    /** 业务日期（期间归属依据，空 = 当天） */
    private LocalDate bizDate;
}
