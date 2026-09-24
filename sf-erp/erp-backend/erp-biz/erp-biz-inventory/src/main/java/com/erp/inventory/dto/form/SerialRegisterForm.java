package com.erp.inventory.dto.form;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 序列号入库登记表单（V422）：批量导入/逐条共用（serialNos 列表一次提交）。
 * <p>
 * 登记只写台账（IN_STOCK），不触发库存数量过账——数量账由对应入库单审核过账。
 */
@Data
public class SerialRegisterForm {

    /** 物料 ID */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /** 入库仓库 ID */
    @NotNull(message = "仓库不能为空")
    private Long warehouseId;

    /** 批次号（可空串） */
    @Size(max = 50, message = "批次号不能超过50字")
    private String batchNo;

    /** 入库来源单号（采购入库单/其他入库单号） */
    @Size(max = 40, message = "来源单号不能超过40字")
    private String sourceNo;

    /** 序列号列表（一次最多 500 条） */
    @NotEmpty(message = "序列号不能为空")
    @Size(max = 500, message = "单次登记不超过500条序列号")
    private List<@Size(max = 64, message = "序列号长度不能超过64") String> serialNos;
}
