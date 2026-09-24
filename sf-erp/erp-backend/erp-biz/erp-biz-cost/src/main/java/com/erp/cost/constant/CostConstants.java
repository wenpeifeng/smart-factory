package com.erp.cost.constant;

import java.time.format.DateTimeFormatter;

/**
 * 成本管理模块公共常量。
 * <p>
 * 汇总此处集中，避免魔法值散落各服务实现。
 */
public final class CostConstants {

    private CostConstants() {
    }

    /** 期间状态：打开（可过账 / 可重算） */
    public static final String PERIOD_OPEN = "OPEN";

    /** 期间状态：已结账（禁止过账 / 禁止重算，单向终态） */
    public static final String PERIOD_CLOSED = "CLOSED";

    /** 凭证状态：草稿（自动生成的结账凭证默认为草稿，待会计复核过账） */
    public static final String VOUCHER_DRAFT = "DRAFT";

    /** 结账凭证字（转账凭证） */
    public static final String VOUCHER_WORD_TRANSFER = "转";

    /** 期间格式 yyyyMM */
    public static final String PERIOD_PATTERN = "yyyyMM";

    /**
     * 期间解析/格式化器（yyyyMM）。
     * <p>
     * 注意：{@code YearMonth.parse(text)} 默认 ISO 格式（yyyy-MM）解析不了
     * "202609"，全模块必须统一经本格式化器解析。
     */
    public static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern(PERIOD_PATTERN);

    /** 金额精度（DECIMAL(18,2)，HALF_UP） */
    public static final int AMOUNT_SCALE = 2;

    /** 单价精度（DECIMAL(18,6)，HALF_UP） */
    public static final int PRICE_SCALE = 6;

    /** 主营业务成本科目编码（借方，销售出库成本结转） */
    public static final String SUBJECT_MAIN_COST = "6401";

    /** 生产成本科目编码（借方，生产领料成本结转） */
    public static final String SUBJECT_PROD_COST = "5001";

    /** 库存商品科目编码（贷方，存货出库结转） */
    public static final String SUBJECT_INVENTORY = "1405";
}
