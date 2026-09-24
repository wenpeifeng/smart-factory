package com.erp.hr.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计件工资明细 VO —— 带员工/订单/物料回显，来源可追溯。
 */
@Data
public class PieceWageDetailVO {

    private Long id;

    /** 期间ID */
    private Long periodId;

    /** 期间（yyyy-MM） */
    private String period;

    /** 员工ID */
    private Long employeeId;

    /** 工号 */
    private String empNo;

    /** 员工姓名 */
    private String employeeName;

    /** 生产订单ID */
    private Long workOrderId;

    /** 生产订单号 */
    private String workOrderNo;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 工序名称 */
    private String processName;

    /** 完成数量 */
    private BigDecimal qty;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 金额 = 数量 × 单价 */
    private BigDecimal amount;

    /** 归集时间 */
    private LocalDateTime createTime;
}
