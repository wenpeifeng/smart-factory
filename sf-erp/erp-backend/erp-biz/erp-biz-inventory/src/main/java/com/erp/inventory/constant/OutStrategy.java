package com.erp.inventory.constant;

/**
 * 批次出库策略 —— {@link com.erp.inventory.service.StockService#stockOut} 的扣减方式。
 * <p>
 * 仅对启用批次管理（base_material.batch_managed=1）的物料生效；
 * 非批次物料无论传何种策略，都走 batch_no='' 汇总行的原逻辑。
 */
public enum OutStrategy {

    /** 先进先出：按批次行 first_in_at 升序依次扣减，可能拆多行流水 */
    FIFO("FIFO", "先进先出"),

    /** 先到期先出（V422）：按批次行 expiry_date 升序（无效期批次按 first_in_at 兜底排后）依次扣减 */
    FEFO("FEFO", "先到期先出"),

    /** 指定批次：只扣 batchNo 对应批次行，不足直接抛业务异常 */
    BATCH("BATCH", "指定批次"),

    /** 总量扣减：不按入库时间排序、跨批次行依次扣足即可（移动平均核算口径），流水仍记录实际批次 */
    AVG("AVG", "总量扣减");

    /** 策略编码（API 入参） */
    private final String code;

    /** 中文说明 */
    private final String label;

    OutStrategy(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 按编码解析策略（大小写不敏感），非法编码抛业务异常。
     */
    public static OutStrategy of(String code) {
        for (OutStrategy s : values()) {
            if (s.code.equalsIgnoreCase(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知的出库策略：" + code);
    }
}
