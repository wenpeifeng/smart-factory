package com.erp.common.enums;

/**
 * 统一单据状态枚举（T7 约定值）—— 仅为"统一语义定义"，不迁移存量数据。
 * <p>
 * 背景：各模块 status 字段历史上各自演化（采购/销售出入库 0/10、订单 0/10/20/30/90、
 * 财务应收应付 UNSETTLED/PARTIAL/SETTLED 字符串……），差异较大，强行统一改动存量
 * 风险高。本枚举给出<b>推荐的目标状态机</b>，新功能（如红冲、联查）按此语义解读存量值：
 * <ul>
 *   <li>存量数值映射（见 docs/modules/reverse-docchain.md 状态盘点表）：
 *       0 → DRAFT，10 → AUDITED，20（订单类=部分执行）→ 视语境 SUBMITTED/PARTIAL，
 *       30（订单类=已完成）→ CLOSED，90（订单关闭）→ CLOSED；</li>
 *   <li>红冲引入的新状态值 20（出入库/退货单）= REVERSED（已红冲，仅本任务新增写入）；</li>
 *   <li>财务应收应付的字符串状态不在本枚举范围（金额维度而非流程维度）。</li>
 * </ul>
 * 新模块建表建议 status 直接采用本枚举 code。
 */
public enum DocStatus {

    /** 草稿（可编辑/删除，未产生任何账务影响） */
    DRAFT(0, "草稿"),

    /** 已提交（进入审批流，不可编辑）——现有模块多为直审，暂未使用 */
    SUBMITTED(5, "已提交"),

    /** 已审核（已过账：库存/应收应付/成本已落账） */
    AUDITED(10, "已审核"),

    /** 已红冲（T7 新增：原单已被负数反向单全额冲减，仅作历史留存） */
    REVERSED(20, "已红冲"),

    /** 已关闭（业务完结，订单类存量 30/90 归入此语义） */
    CLOSED(90, "已关闭");

    /** 推荐存储值（status 列直接存 code） */
    private final int code;

    /** 中文说明 */
    private final String label;

    DocStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 数值转枚举；无法识别返回 null（调用方自行兜底，不抛异常） */
    public static DocStatus ofCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (DocStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
