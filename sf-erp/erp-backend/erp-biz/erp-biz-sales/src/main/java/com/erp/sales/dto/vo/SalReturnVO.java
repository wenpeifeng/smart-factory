package com.erp.sales.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售退货单列表/详情响应 VO（表头）。
 */
@Data
public class SalReturnVO {

    /** 退货单 ID */
    private Long id;

    /** 退货单号 */
    private String returnNo;

    /** 来源出库单 ID */
    private Long outId;

    /** 来源出库单号 */
    private String outNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户编码（关联 base_customer） */
    private String customerCode;

    /** 客户名称（关联 base_customer） */
    private String customerName;

    /** 仓库 ID */
    private Long warehouseId;

    /** 仓库名称（关联 base_warehouse） */
    private String warehouseName;

    /** 退货日期 yyyy-MM-dd */
    private LocalDate returnDate;

    /** 总数量（正数表述） */
    private BigDecimal totalQty;

    /** 总金额（正数表述，应收红字取负值） */
    private BigDecimal totalAmount;

    /** 状态：0草稿 10已审核 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditTime;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
