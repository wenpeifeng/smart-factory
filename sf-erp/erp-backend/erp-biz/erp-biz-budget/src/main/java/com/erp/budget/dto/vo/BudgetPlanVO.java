package com.erp.budget.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预算方案 VO（列表/详情通用，含控制配置摘要与编制人姓名）。
 */
@Data
public class BudgetPlanVO {

    /** 方案 ID */
    private Long id;

    /** 方案编码 */
    private String planCode;

    /** 方案名称 */
    private String planName;

    /** 预算年度 */
    private Integer budgetYear;

    /** 状态：DRAFT/RELEASED/CLOSED */
    private String status;

    /** 状态文案：草稿/已下达/已关闭 */
    private String statusText;

    /** 预算总额 */
    private BigDecimal totalAmount;

    /** 控制方式：WARN/BLOCK（无配置为 null） */
    private String controlWay;

    /** 超预算阈值百分比（无配置为 null） */
    private BigDecimal overPercent;

    /** 编制人 ID */
    private Long prepareBy;

    /** 编制人姓名（sys_user.nickname 只读关联） */
    private String prepareByName;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
