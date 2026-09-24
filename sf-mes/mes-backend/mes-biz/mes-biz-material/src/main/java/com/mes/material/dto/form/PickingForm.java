package com.mes.material.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 领料单创建表单（头 + 行）。
 */
@Data
public class PickingForm {
    /** 工单 ID */
    @NotNull(message = "工单不能为空")
    private Long workorderId;
    /** 发料仓库 ID */
    @NotNull(message = "仓库不能为空")
    private Long warehouseId;
    /** 领料类型：NORMAL/URGENT */
    private String pickType;
    /** 备注 */
    private String remark;
    /** 行项目 */
    @NotEmpty(message = "领料行不能为空")
    @Valid
    private List<Item> items;

    /** 领料行表单 */
    @Data
    public static class Item {
        /** 物料 ID */
        @NotNull(message = "物料不能为空")
        private Long materialId;
        /** 批次号 */
        @NotBlank(message = "批次号不能为空（批次不管理物料填 -）")
        private String batchNo;
        /** 领料数量 */
        @NotNull(message = "数量不能为空")
        @Positive(message = "数量必须大于0")
        private BigDecimal qty;
        /** 备注 */
        private String remark;
    }
}
