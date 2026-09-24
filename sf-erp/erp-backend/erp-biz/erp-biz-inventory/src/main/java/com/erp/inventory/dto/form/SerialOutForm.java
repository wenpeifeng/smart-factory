package com.erp.inventory.dto.form;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 序列号出库核销表单（V422）：出库时传序列号列表核销（status IN_STOCK → OUT）。
 * <p>
 * 校验：全部序列号必须存在且为 IN_STOCK，任一不满足整体回滚；
 * 核销只写台账（out_no），不触发库存数量过账。
 */
@Data
public class SerialOutForm {

    /** 出库单号 */
    @Size(max = 40, message = "出库单号不能超过40字")
    private String outNo;

    /** 序列号列表 */
    @NotEmpty(message = "序列号不能为空")
    @Size(max = 500, message = "单次核销不超过500条序列号")
    private List<@Size(max = 64, message = "序列号长度不能超过64") String> serialNos;
}
