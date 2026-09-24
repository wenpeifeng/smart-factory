package com.mes.material.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 退料单创建表单（头 + 行）。
 */
@Data
public class ReturnForm {
    /** 工单 ID（余料退库可为空） */
    private Long workorderId;
    /** 退料类型：LEFTOVER/REJECT */
    private String returnType;
    /** 退回仓库 ID */
    @NotNull(message = "仓库不能为空")
    private Long warehouseId;
    /** 备注 */
    private String remark;
    /** 行项目 */
    @NotEmpty(message = "退料行不能为空")
    @Valid
    private List<Item> items;

    /** 退料行表单 */
    @Data
    public static class Item {
        /** 物料 ID */
        @NotNull(message = "物料不能为空")
        private Long materialId;
        /** 批次号 */
        private String batchNo;
        /** 退料数量 */
        @NotNull(message = "数量不能为空")
        @Positive(message = "数量必须大于0")
        private BigDecimal qty;
        /** 备注 */
        private String remark;
    }
}
