package com.erp.sales.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发货通知单按出库单号核销表单。
 * <p>
 * 出库挂点约定：出库单创建时在 remark 记录 FH 通知单号，
 * 出库审核后在此录入出库单号完成核销（守卫不超通知数量）。
 */
@Data
public class NoticeSettleForm {

    /** 已审核销售出库单号（XS 前缀） */
    @NotBlank(message = "出库单号不能为空")
    @Size(max = 40, message = "出库单号最长 40 字")
    private String outstockNo;
}
