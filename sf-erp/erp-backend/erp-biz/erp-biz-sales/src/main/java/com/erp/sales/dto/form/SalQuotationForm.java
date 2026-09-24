package com.erp.sales.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售报价单新增/修改提交表单。
 * <p>
 * 成本参考不在此承接（服务端从 base_material.ref_cost 取快照，防前端篡改）。
 */
@Data
public class SalQuotationForm {

    /** 客户 ID */
    @NotNull(message = "客户不能为空")
    private Long customerId;

    /** 报价日期（不允许晚于今天） */
    @NotNull(message = "报价日期不能为空")
    @PastOrPresent(message = "报价日期不能晚于当天")
    private LocalDate quoteDate;

    /** 有效期至（不允许早于报价日期） */
    @NotNull(message = "有效期至不能为空")
    private LocalDate validUntil;

    /** 整单折扣率%（0-100，空 = 不打折） */
    private BigDecimal discountRate;

    /** 备注 */
    @Size(max = 500, message = "备注最长 500 字")
    private String remark;

    /** 明细行（至少一行） */
    @NotEmpty(message = "报价明细不能为空")
    @Valid
    private List<SalQuotationItemForm> items;
}
