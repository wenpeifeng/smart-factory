package com.erp.purchase.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 请购单新增/修改表单。
 * <p>
 * applicantId 可空（缺省当前登录用户）；明细行的已转采购数量不可提交，
 * 一律由询比价定标事务回写。
 */
@Data
public class PurRequisitionForm {

    /** 请购部门 ID（必填，sys_dept.id） */
    @NotNull(message = "请购部门不能为空")
    private Long deptId;

    /** 请购人 ID（可空=当前登录用户） */
    private Long applicantId;

    /** 期望到货日期（必填） */
    @NotNull(message = "期望日期不能为空")
    private LocalDate expectDate;

    /** 备注 */
    @Size(max = 500, message = "备注最长 500 字")
    private String remark;

    /** 请购明细（至少一行） */
    @NotEmpty(message = "请购明细不能为空")
    @Valid
    private List<ItemForm> items;

    /**
     * 请购明细行。
     */
    @Data
    public static class ItemForm {

        /** 物料 ID（必填） */
        @NotNull(message = "物料不能为空")
        private Long materialId;

        /** 请购数量（必填，>0） */
        @NotNull(message = "数量不能为空")
        @DecimalMin(value = "0.0001", message = "数量必须大于 0")
        private BigDecimal qty;

        /** 需求日期 */
        private LocalDate requireDate;

        /** 行备注 */
        @Size(max = 200, message = "行备注最长 200 字")
        private String remark;
    }
}
