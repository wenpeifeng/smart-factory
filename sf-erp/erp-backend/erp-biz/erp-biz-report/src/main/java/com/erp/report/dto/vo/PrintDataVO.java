package com.erp.report.dto.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 标准打印数据 VO —— 打印中心 5 类单据模板的统一出参结构。
 * <p>
 * 设计目标：前端只实现一个 A4 版式组件（PrintPage），按本 VO 的
 * 标题/主信息区/明细列/合计区/签字栏元数据渲染，后端负责所有口径与格式
 * （金额千分位 2 位小数、数量去尾零、日期 yyyy-MM-dd、状态转中文），
 * 前端零格式化逻辑，保证打印物全站一致。
 * <p>
 * bizType 取值：sal_order（销售订单）/ delivery_note（销售出库·送货单）/
 * pur_order（采购订单）/ statement（客户月度对账单）/ voucher（记账凭证）。
 */
@Data
public class PrintDataVO {

    /** 业务类型（同接口 path 参数） */
    private String bizType;

    /** 公司抬头（打印页顶部公司名） */
    private String company;

    /** 单据标题（如"销售订单"、"记账凭证"） */
    private String title;

    /** 单据编号（对账单为对账单号 DZ-客户编码-年月） */
    private String billNo;

    /** 单据日期 yyyy-MM-dd（对账单为期间 yyyy-MM，凭证为凭证日期） */
    private String billDate;

    /** 主信息区：标签-值对（客户/供应商/仓库/联系人等），前端按两列表格铺开 */
    private List<PrintField> headerFields;

    /** 明细表列定义（顺序即展示顺序） */
    private List<PrintColumn> columns;

    /**
     * 明细行：key = {@link PrintColumn#getKey()}，value 已格式化为展示字符串
     * （金额千分位、数量去尾零），前端直接渲染
     */
    private List<Map<String, String>> details;

    /** 合计区：标签-值对（合计数量/合计金额/期初期末等，顺序展示） */
    private List<PrintField> totals;

    /** 合计金额大写（壹仟贰佰叁拾肆元伍角陆分；无金额单据为 null） */
    private String amountInWords;

    /** 附单据说明（凭证专用，如"附单据 3 张"；其他单据 null） */
    private String attachmentNote;

    /** 签字栏标签（顺序展示，如 制单/审核/批准/客户签收） */
    private List<String> signatures;

    /** 表尾备注（单据 remark，可空） */
    private String remark;

    /** 主单据金额（数字口径，供前端做小计校验/展示，可空） */
    private java.math.BigDecimal totalAmount;

    /** 主信息区/合计区字段 */
    @Data
    public static class PrintField {
        /** 字段标签（如"客户名称"） */
        private String label;
        /** 展示值（已格式化，空数据兜底为空串或占位符） */
        private String value;

        public PrintField() {
        }

        public PrintField(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    /** 明细表列定义 */
    @Data
    public static class PrintColumn {
        /** 列 key（对应 details 行 Map 的 key） */
        private String key;
        /** 列头标题 */
        private String label;
        /** 列宽（百分比数字 1~100，列宽之和建议 100；空则均分） */
        private Integer width;
        /** 对齐方式：left/center/right（金额右对齐、序号居中） */
        private String align;

        public PrintColumn() {
        }

        public PrintColumn(String key, String label, Integer width, String align) {
            this.key = key;
            this.label = label;
            this.width = width;
            this.align = align;
        }
    }
}
