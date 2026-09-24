package com.erp.sales.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 发货通知单新增/修改提交表单。
 * <p>
 * 两种来源二选一：
 * <ul>
 *   <li>从订单生成：orderId 必填，明细引用 orderDetailId（守卫订单行余量），
 *       客户/仓库缺省取订单值；</li>
 *   <li>无来源直发：orderId 为空，customerId/warehouseId 必填，
 *       明细填 materialId。</li>
 * </ul>
 */
@Data
public class SalDeliveryNoticeForm {

    /** 来源销售订单 ID（空 = 无来源直发） */
    private Long orderId;

    /** 客户 ID（直发必填；带订单时缺省取订单客户） */
    private Long customerId;

    /** 发货仓库 ID（直发必填；带订单时缺省取订单仓库） */
    private Long warehouseId;

    /** 计划发货日期 */
    @NotNull(message = "计划发货日期不能为空")
    private LocalDate planDate;

    /** 备注 */
    @Size(max = 500, message = "备注最长 500 字")
    private String remark;

    /** 明细行（至少一行） */
    @NotEmpty(message = "通知明细不能为空")
    @Valid
    private List<SalDeliveryNoticeItemForm> details;
}
