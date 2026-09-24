package com.erp.hr.constant;

import java.math.BigDecimal;

/**
 * 人力资源模块公共常量 —— 状态机取值与固定业务参数集中定义，
 * 避免字符串魔法值散落在 Service/Controller 之间造成不一致。
 */
public final class HrConstants {

    private HrConstants() {
    }

    // ==================== 员工状态 ====================
    /** 员工：在职 */
    public static final String EMP_ON_JOB = "ON_JOB";
    /** 员工：离职 */
    public static final String EMP_RESIGNED = "RESIGNED";

    /** 性别：男 */
    public static final int GENDER_MALE = 1;
    /** 性别：女 */
    public static final int GENDER_FEMALE = 2;

    // ==================== 考勤状态 ====================
    /** 考勤：正常 */
    public static final String ATT_NORMAL = "NORMAL";
    /** 考勤：缺勤 */
    public static final String ATT_ABSENT = "ABSENT";
    /** 考勤：迟到 */
    public static final String ATT_LATE = "LATE";
    /** 考勤：请假 */
    public static final String ATT_LEAVE = "LEAVE";
    /** 考勤：加班 */
    public static final String ATT_OVERTIME = "OVERTIME";

    // ==================== 计件价目状态 ====================
    /** 价目：启用 */
    public static final String PRICE_ENABLED = "ENABLED";
    /** 价目：停用 */
    public static final String PRICE_DISABLED = "DISABLED";

    // ==================== 计件工资期间状态 ====================
    /** 期间：开账（可归集/可结账） */
    public static final String WAGE_OPEN = "OPEN";
    /** 期间：已结账（拒绝再归集） */
    public static final String WAGE_CLOSED = "CLOSED";

    // ==================== 工资汇总固定参数（简化模型，暂用常量） ====================
    /** 加班费单价（元/天） */
    public static final BigDecimal OVERTIME_ALLOWANCE_PER_DAY = new BigDecimal("50.00");
    /** 缺勤扣款折算天数基准：月薪基准 / 30 × 缺勤天数 */
    public static final BigDecimal ABSENT_DEDUCT_BASE_DAYS = new BigDecimal("30");

    /** 员工工号前缀 */
    public static final String PREFIX_EMP_NO = "EMP";
}
