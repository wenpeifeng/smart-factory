package com.erp.hr.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计件归集数据源 —— 完工生产订单行（mrp_work_order 只读约定列）。
 */
@Data
public class WorkOrderSourceVO {

    /** 生产订单ID */
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 成品物料ID */
    private Long materialId;

    /** 累计完工良品数量 */
    private BigDecimal finishQty;

    /** 制单人（sys_user.id，用于归属 hr_employee.user_id） */
    private Long createBy;

    /** 最后更新时间（完工月份近似口径） */
    private LocalDateTime updateTime;
}
