package com.erp.sales.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售订单列表/详情响应 VO（表头）。
 * <p>
 * 客户编码/名称、仓库名称由 Mapper 关联 base_customer / base_warehouse 查询带出。
 */
@Data
public class SalOrderVO {

    /** 订单 ID */
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户编码（关联 base_customer） */
    private String customerCode;

    /** 客户名称（关联 base_customer） */
    private String customerName;

    /** 发货仓库 ID */
    private Long warehouseId;

    /** 仓库名称（关联 base_warehouse） */
    private String warehouseName;

    /** 订单日期 yyyy-MM-dd */
    private LocalDate orderDate;

    /** 总数量 */
    private BigDecimal totalQty;

    /** 总金额 */
    private BigDecimal totalAmount;

    /** 状态：0草稿 10已审核 20部分出库 30已完成 90已关闭 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditTime;

    /** 关闭时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime closeTime;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
