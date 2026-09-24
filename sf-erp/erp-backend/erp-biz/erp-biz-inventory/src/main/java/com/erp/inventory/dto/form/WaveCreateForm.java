package com.erp.inventory.dto.form;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 建波表单（V421）：选多张已审核出库单 → 生成拣选任务并入波。
 */
@Data
public class WaveCreateForm {

    /** 仓库 ID（波次归属仓，需与所选出库单一致） */
    @NotNull(message = "仓库不能为空")
    private Long warehouseId;

    /** 出库单选择（docId + sourceType 定位一张单） */
    @NotEmpty(message = "请至少选择一张已审核出库单")
    private List<DocRef> docs;

    /** 备注 */
    @Size(max = 200, message = "备注不能超过200字")
    private String remark;

    /** 出库单引用 */
    @Data
    public static class DocRef {

        /** 出库单 ID */
        @NotNull(message = "出库单不能为空")
        private Long docId;

        /** 来源类型：SAL_OUT 销售出库 / OTH_OUT 其他出库 */
        @NotNull(message = "来源类型不能为空")
        private String sourceType;
    }
}
