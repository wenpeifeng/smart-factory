package com.erp.report.service.impl;

import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.report.dto.vo.PrintDataVO;
import com.erp.report.dto.vo.PrintDocRowVO;
import com.erp.report.mapper.PrintMapper;
import com.erp.report.service.PrintService;
import com.erp.report.support.AmountInWords;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单据打印中心服务实现：把各模块主子表聚合装配为标准打印 VO。
 * <p>
 * 装配约定（前端 PrintPage 零逻辑渲染的前提）：
 * <ul>
 *   <li>所有展示值在服务端格式化：金额千分位 2 位小数、数量去尾零、
 *       税率 13.00→13%、日期由 SQL DATE_FORMAT 输出；</li>
 *   <li>空数据兜底：文本列空值输出空串，金额/数量空值输出 0.00 / 0；</li>
 *   <li>主子表按单据 id 直查（草稿可打印）；对账单只取已审核出库（对账口径）；</li>
 *   <li>合计以明细行重算为准（主表合计字段仅回显校验），大写金额由
 *       {@link AmountInWords} 输出。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PrintServiceImpl implements PrintService {

    /** 公司抬头（单据打印统一落款，后续可配置化到系统参数） */
    private static final String COMPANY = "苏州精工机械有限公司";

    /** 合法的业务类型 */
    private static final Set<String> BIZ_TYPES =
            Set.of("sal_order", "delivery_note", "pur_order", "statement", "voucher");

    /** 订单类单据状态 → 中文（sal_order / pur_order 同一状态机） */
    private static final Map<Integer, String> ORDER_STATUS = Map.of(
            0, "草稿", 10, "已审核", 20, "部分出入库", 30, "已完成", 90, "已关闭");

    /** 出库/入库单状态 → 中文 */
    private static final Map<Integer, String> BIZ_STATUS = Map.of(0, "草稿", 10, "已审核");

    /** 凭证状态 → 中文 */
    private static final Map<String, String> VOUCHER_STATUS = Map.of("DRAFT", "草稿", "POSTED", "已过账");

    private final PrintMapper printMapper;

    // ==================== 单据选择列表 ====================

    @Override
    public PageResult<PrintDocRowVO> listDocs(String bizType, int pageNum, int pageSize, String keyword, String month) {
        String type = requireBizType(bizType);
        int page = Math.max(pageNum, 1);
        int size = Math.min(Math.max(pageSize, 1), 100);
        long offset = (long) (page - 1) * size;

        long total;
        List<Map<String, Object>> rows;
        if ("statement".equals(type)) {
            LocalDate[] range = monthRange(month);
            total = printMapper.countStatementCustomers(range[0], range[1]);
            rows = printMapper.listStatementCustomers(range[0], range[1], offset, size);
        } else {
            total = switch (type) {
                case "sal_order" -> printMapper.countSalOrders(keyword);
                case "delivery_note" -> printMapper.countOutstocks(keyword);
                case "pur_order" -> printMapper.countPurOrders(keyword);
                default -> printMapper.countVouchers(keyword);
            };
            rows = switch (type) {
                case "sal_order" -> printMapper.listSalOrders(keyword, offset, size);
                case "delivery_note" -> printMapper.listOutstocks(keyword, offset, size);
                case "pur_order" -> printMapper.listPurOrders(keyword, offset, size);
                default -> printMapper.listVouchers(keyword, offset, size);
            };
        }
        return PageResult.of(rows.stream().map(this::toDocRow).toList(), total, page, size);
    }

    // ==================== 打印数据装配 ====================

    @Override
    public PrintDataVO buildPrintData(String bizType, Long id, String month) {
        String type = requireBizType(bizType);
        if (id == null || id <= 0) {
            throw BusinessException.ofKey("erp.report.doc-id-invalid");
        }
        return switch (type) {
            case "sal_order" -> buildSalOrder(id);
            case "delivery_note" -> buildDeliveryNote(id);
            case "pur_order" -> buildPurOrder(id);
            case "statement" -> buildStatement(id, month);
            default -> buildVoucher(id);
        };
    }

    /** 销售订单：订单主子表 + 客户信息 + 金额合计/大写 */
    private PrintDataVO buildSalOrder(Long id) {
        Map<String, Object> head = requireHead(printMapper.selectSalOrder(id), "销售订单");
        List<Map<String, Object>> details = printMapper.selectSalOrderDetails(id);

        PrintDataVO vo = base("sal_order", "销售订单", str(head, "orderNo"), str(head, "orderDate"));
        vo.setHeaderFields(List.of(
                new PrintDataVO.PrintField("客户名称", nvl(str(head, "custName"))),
                new PrintDataVO.PrintField("客户编码", nvl(str(head, "custCode"))),
                new PrintDataVO.PrintField("联系人", nvl(str(head, "custContact"))),
                new PrintDataVO.PrintField("联系电话", nvl(str(head, "custPhone"))),
                new PrintDataVO.PrintField("收货仓库", nvl(str(head, "warehouseName"))),
                new PrintDataVO.PrintField("单据状态", ORDER_STATUS.getOrDefault(intVal(head.get("status")), "-"))));
        vo.setColumns(List.of(
                col("seq", "序号", 6, "center"),
                col("matCode", "物料编码", 14),
                col("matName", "物料名称", 20),
                col("matSpec", "规格型号", 14),
                col("unitName", "单位", 6, "center"),
                col("qty", "数量", 9, "right"),
                col("price", "单价", 10, "right"),
                col("amount", "金额", 12, "right"),
                col("remark", "备注", 9)));
        List<Map<String, String>> rows = new ArrayList<>();
        BigDecimal qtyTotal = BigDecimal.ZERO;
        BigDecimal amtTotal = BigDecimal.ZERO;
        int seq = 0;
        for (Map<String, Object> d : details) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("seq", String.valueOf(++seq));
            row.put("matCode", nvl(str(d, "matCode")));
            row.put("matName", nvl(str(d, "matName")));
            row.put("matSpec", nvl(str(d, "matSpec")));
            row.put("unitName", nvl(str(d, "unitName")));
            row.put("qty", fmtQty(dec(d.get("qty"))));
            row.put("price", fmtMoney(dec(d.get("price"))));
            row.put("amount", fmtMoney(dec(d.get("amount"))));
            row.put("remark", nvl(str(d, "remark")));
            qtyTotal = qtyTotal.add(dec(d.get("qty")));
            amtTotal = amtTotal.add(dec(d.get("amount")));
            rows.add(row);
        }
        vo.setDetails(rows);
        vo.setTotalAmount(amtTotal);
        vo.setTotals(List.of(
                new PrintDataVO.PrintField("合计数量", fmtQty(qtyTotal)),
                new PrintDataVO.PrintField("合计金额", fmtMoney(amtTotal))));
        vo.setAmountInWords(AmountInWords.toWords(amtTotal));
        vo.setRemark(str(head, "remark"));
        vo.setSignatures(signatureLabels(str(head, "makerName"), str(head, "auditorName"),
                "批准", "客户确认"));
        return vo;
    }

    /** 销售出库单（送货单样式）：客户/仓库/物料/数量/订单号 + 客户签收栏 */
    private PrintDataVO buildDeliveryNote(Long id) {
        Map<String, Object> head = requireHead(printMapper.selectOutstock(id), "销售出库单");
        List<Map<String, Object>> details = printMapper.selectOutstockDetails(id);

        PrintDataVO vo = base("delivery_note", "销售出库单（送货单）", str(head, "outNo"), str(head, "outDate"));
        vo.setHeaderFields(List.of(
                new PrintDataVO.PrintField("客户名称", nvl(str(head, "custName"))),
                new PrintDataVO.PrintField("客户编码", nvl(str(head, "custCode"))),
                new PrintDataVO.PrintField("联系人", nvl(str(head, "custContact"))),
                new PrintDataVO.PrintField("联系电话", nvl(str(head, "custPhone"))),
                new PrintDataVO.PrintField("发货仓库", nvl(str(head, "warehouseName"))),
                new PrintDataVO.PrintField("关联订单号", nvl(str(head, "orderNo"))),
                new PrintDataVO.PrintField("单据状态", BIZ_STATUS.getOrDefault(intVal(head.get("status")), "-")),
                new PrintDataVO.PrintField("收货地址", nvl(str(head, "custAddress")))));
        vo.setColumns(List.of(
                col("seq", "序号", 6, "center"),
                col("matCode", "物料编码", 14),
                col("matName", "物料名称", 22),
                col("matSpec", "规格型号", 14),
                col("unitName", "单位", 6, "center"),
                col("qty", "数量", 10, "right"),
                col("price", "单价", 10, "right"),
                col("amount", "金额", 12, "right"),
                col("remark", "备注", 6)));
        List<Map<String, String>> rows = new ArrayList<>();
        BigDecimal qtyTotal = BigDecimal.ZERO;
        BigDecimal amtTotal = BigDecimal.ZERO;
        int seq = 0;
        for (Map<String, Object> d : details) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("seq", String.valueOf(++seq));
            row.put("matCode", nvl(str(d, "matCode")));
            row.put("matName", nvl(str(d, "matName")));
            row.put("matSpec", nvl(str(d, "matSpec")));
            row.put("unitName", nvl(str(d, "unitName")));
            row.put("qty", fmtQty(dec(d.get("qty"))));
            row.put("price", fmtMoney(dec(d.get("price"))));
            row.put("amount", fmtMoney(dec(d.get("amount"))));
            row.put("remark", nvl(str(d, "remark")));
            qtyTotal = qtyTotal.add(dec(d.get("qty")));
            amtTotal = amtTotal.add(dec(d.get("amount")));
            rows.add(row);
        }
        vo.setDetails(rows);
        vo.setTotalAmount(amtTotal);
        vo.setTotals(List.of(
                new PrintDataVO.PrintField("合计数量", fmtQty(qtyTotal)),
                new PrintDataVO.PrintField("合计金额（含税）", fmtMoney(amtTotal))));
        vo.setAmountInWords(AmountInWords.toWords(amtTotal));
        vo.setRemark(str(head, "remark"));
        // 送货单特有：客户签收栏（对方签收 + 签收日期）
        vo.setSignatures(signatureLabels(str(head, "makerName"), str(head, "auditorName"),
                "批准", "客户签收"));
        return vo;
    }

    /** 采购订单：主子表 + 供应商 + 税率/交货日期列 */
    private PrintDataVO buildPurOrder(Long id) {
        Map<String, Object> head = requireHead(printMapper.selectPurOrder(id), "采购订单");
        List<Map<String, Object>> details = printMapper.selectPurOrderDetails(id);

        PrintDataVO vo = base("pur_order", "采购订单", str(head, "orderNo"), str(head, "orderDate"));
        vo.setHeaderFields(List.of(
                new PrintDataVO.PrintField("供应商名称", nvl(str(head, "suppName"))),
                new PrintDataVO.PrintField("供应商编码", nvl(str(head, "suppCode"))),
                new PrintDataVO.PrintField("联系人", nvl(str(head, "suppContact"))),
                new PrintDataVO.PrintField("联系电话", nvl(str(head, "suppPhone"))),
                new PrintDataVO.PrintField("收货仓库", nvl(str(head, "warehouseName"))),
                new PrintDataVO.PrintField("单据状态", ORDER_STATUS.getOrDefault(intVal(head.get("status")), "-"))));
        vo.setColumns(List.of(
                col("seq", "序号", 5, "center"),
                col("matCode", "物料编码", 13),
                col("matName", "物料名称", 18),
                col("matSpec", "规格型号", 13),
                col("unitName", "单位", 6, "center"),
                col("qty", "数量", 9, "right"),
                col("price", "单价", 9, "right"),
                col("taxRate", "税率%", 7, "right"),
                col("amount", "金额", 11, "right"),
                col("deliveryDate", "交货日期", 9, "center")));
        List<Map<String, String>> rows = new ArrayList<>();
        BigDecimal amtTotal = BigDecimal.ZERO;
        int seq = 0;
        for (Map<String, Object> d : details) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("seq", String.valueOf(++seq));
            row.put("matCode", nvl(str(d, "matCode")));
            row.put("matName", nvl(str(d, "matName")));
            row.put("matSpec", nvl(str(d, "matSpec")));
            row.put("unitName", nvl(str(d, "unitName")));
            row.put("qty", fmtQty(dec(d.get("qty"))));
            row.put("price", fmtMoney(dec(d.get("price"))));
            row.put("taxRate", fmtRate(dec(d.get("taxRate"))));
            row.put("amount", fmtMoney(dec(d.get("amount"))));
            row.put("deliveryDate", nvl(str(d, "deliveryDate")));
            amtTotal = amtTotal.add(dec(d.get("amount")));
            rows.add(row);
        }
        vo.setDetails(rows);
        vo.setTotalAmount(amtTotal);
        vo.setTotals(List.of(
                new PrintDataVO.PrintField("价税合计", fmtMoney(amtTotal)),
                new PrintDataVO.PrintField("大写", AmountInWords.toWords(amtTotal))));
        vo.setAmountInWords(AmountInWords.toWords(amtTotal));
        vo.setRemark(str(head, "remark"));
        vo.setSignatures(signatureLabels(str(head, "makerName"), str(head, "auditorName"),
                "批准", "供方确认"));
        return vo;
    }

    /** 客户月度对账单：当月已审核出库明细 + 期初/本期/期末应收 + 大写 */
    private PrintDataVO buildStatement(Long customerId, String month) {
        LocalDate[] range = monthRange(month);
        YearMonth ym = YearMonth.from(range[0]);
        Map<String, Object> cust = requireHead(printMapper.selectCustomer(customerId), "客户");
        List<Map<String, Object>> rows = printMapper.selectStatementRows(customerId, range[0], range[1]);

        BigDecimal opening = nz(printMapper.selectCustomerOpening(customerId, range[0]));
        BigDecimal current = BigDecimal.ZERO;
        int seq = 0;
        List<Map<String, String>> details = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("seq", String.valueOf(++seq));
            row.put("outDate", nvl(str(r, "outDate")));
            row.put("outNo", nvl(str(r, "outNo")));
            row.put("orderNo", nvl(str(r, "orderNo")));
            row.put("matCode", nvl(str(r, "matCode")));
            row.put("matName", nvl(str(r, "matName")));
            row.put("qty", fmtQty(dec(r.get("qty"))));
            row.put("amount", fmtMoney(dec(r.get("amount"))));
            current = current.add(dec(r.get("amount")));
            details.add(row);
        }
        BigDecimal ending = opening.add(current);

        String monthCn = ym.getYear() + "年" + String.format("%02d", ym.getMonthValue()) + "月";
        PrintDataVO vo = base("statement", "客户月度对账单",
                "DZ-" + nvl(str(cust, "custCode")) + "-" + ym, ym.toString());
        vo.setBillDate(ym.getYear() + "-" + String.format("%02d", ym.getMonthValue()));
        vo.setHeaderFields(List.of(
                new PrintDataVO.PrintField("客户名称", nvl(str(cust, "custName"))),
                new PrintDataVO.PrintField("客户编码", nvl(str(cust, "custCode"))),
                new PrintDataVO.PrintField("联系人", nvl(str(cust, "custContact"))),
                new PrintDataVO.PrintField("联系电话", nvl(str(cust, "custPhone"))),
                new PrintDataVO.PrintField("对账期间", monthCn),
                new PrintDataVO.PrintField("制表日期", LocalDate.now().toString())));
        vo.setColumns(List.of(
                col("seq", "序号", 6, "center"),
                col("outDate", "出库日期", 11, "center"),
                col("outNo", "出库单号", 16),
                col("orderNo", "订单编号", 16),
                col("matCode", "物料编码", 13),
                col("matName", "物料名称", 18),
                col("qty", "数量", 8, "right"),
                col("amount", "金额", 12, "right")));
        vo.setDetails(details);
        vo.setTotalAmount(ending);
        vo.setTotals(List.of(
                new PrintDataVO.PrintField("期初应收", fmtMoney(opening)),
                new PrintDataVO.PrintField("本期应收发生", fmtMoney(current)),
                new PrintDataVO.PrintField("期末应收", fmtMoney(ending))));
        vo.setAmountInWords(AmountInWords.toWords(ending));
        vo.setRemark("对账说明：以上金额为 " + monthCn + "已审核销售出库（含税）汇总；请核对无误后签章确认。");
        vo.setSignatures(List.of("制单：____________", "审核：____________", "客户确认：____________"));
        return vo;
    }

    /** 记账凭证（金蝶样式）：摘要/科目/借方/贷方 + 合计行 + 附单据张数 */
    private PrintDataVO buildVoucher(Long id) {
        Map<String, Object> head = requireHead(printMapper.selectVoucher(id), "记账凭证");
        List<Map<String, Object>> entries = printMapper.selectVoucherEntries(id);

        PrintDataVO vo = base("voucher", "记账凭证", str(head, "voucherNo"), str(head, "voucherDate"));
        vo.setHeaderFields(List.of(
                new PrintDataVO.PrintField("凭证字号", nvl(str(head, "word")) + " 字 第 " + nvl(str(head, "voucherNo")) + " 号"),
                new PrintDataVO.PrintField("凭证日期", nvl(str(head, "voucherDate"))),
                new PrintDataVO.PrintField("凭证状态", VOUCHER_STATUS.getOrDefault(nvl(str(head, "status")), "-"))));
        vo.setAttachmentNote("附单据 " + intVal(head.get("attachmentCount")) + " 张");
        vo.setColumns(List.of(
                col("lineNo", "行号", 6, "center"),
                col("summary", "摘要", 30),
                col("subjectCode", "科目编码", 14),
                col("subjectName", "科目名称", 22),
                col("debitAmount", "借方金额", 14, "right"),
                col("creditAmount", "贷方金额", 14, "right")));
        List<Map<String, String>> rows = new ArrayList<>();
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        for (Map<String, Object> e : entries) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("lineNo", String.valueOf(intVal(e.get("lineNo"))));
            row.put("summary", nvl(str(e, "summary")));
            row.put("subjectCode", nvl(str(e, "subjectCode")));
            row.put("subjectName", nvl(str(e, "subjectName")));
            row.put("debitAmount", fmtMoney(dec(e.get("debitAmount"))));
            row.put("creditAmount", fmtMoney(dec(e.get("creditAmount"))));
            debitTotal = debitTotal.add(dec(e.get("debitAmount")));
            creditTotal = creditTotal.add(dec(e.get("creditAmount")));
            rows.add(row);
        }
        vo.setDetails(rows);
        vo.setTotalAmount(debitTotal);
        vo.setTotals(List.of(
                new PrintDataVO.PrintField("借方合计", fmtMoney(debitTotal)),
                new PrintDataVO.PrintField("贷方合计", fmtMoney(creditTotal))));
        vo.setAmountInWords(AmountInWords.toWords(debitTotal));
        vo.setRemark(str(head, "remark"));
        vo.setSignatures(signatureLabels(str(head, "makerName"), str(head, "auditorName"), "记账", "出纳"));
        return vo;
    }

    // ==================== 私有工具 ====================

    /** bizType 归一化校验：非法值直接业务异常 */
    private String requireBizType(String bizType) {
        if (bizType == null || !BIZ_TYPES.contains(bizType)) {
            throw BusinessException.ofKey("erp.report.biztype-unsupported", String.join("/", BIZ_TYPES));
        }
        return bizType;
    }

    /** 主单据头查询：不存在（或已删除）→ 业务异常 */
    private Map<String, Object> requireHead(Map<String, Object> head, String label) {
        if (head == null) {
            throw BusinessException.ofKey("erp.report.not-exists-or-deleted", label);
        }
        return head;
    }

    /** 通用骨架：类型/标题/单号/日期/公司 + 标准签字栏（制单/审核人实名） */
    private PrintDataVO base(String bizType, String title, String billNo, String billDate) {
        PrintDataVO vo = new PrintDataVO();
        vo.setBizType(bizType);
        vo.setCompany(COMPANY);
        vo.setTitle(title);
        vo.setBillNo(nvl(billNo));
        vo.setBillDate(nvl(billDate));
        return vo;
    }

    /** 签字栏：制单人/审核人带姓名落款，其余为留白签字位 */
    private List<String> signatureLabels(String maker, String auditor, String... others) {
        List<String> labels = new ArrayList<>();
        labels.add("制单：" + nvl(maker));
        labels.add("审核：" + nvl(auditor));
        for (String other : others) {
            labels.add(other + "：____________");
        }
        return labels;
    }

    /** 列表行 Map → PrintDocRowVO */
    private PrintDocRowVO toDocRow(Map<String, Object> row) {
        PrintDocRowVO vo = new PrintDocRowVO();
        vo.setId(row.get("id") instanceof Number n ? n.longValue() : null);
        vo.setBillNo(nvl(str(row, "billNo")));
        vo.setPartyName(nvl(str(row, "partyName")));
        vo.setBillDate(nvl(str(row, "billDate")));
        vo.setAmount(nz(dec(row.get("amount"))));
        Object status = row.get("status");
        if (status instanceof Number num) {
            // 出库单 0/10 与订单状态机同名；20/30/90 回退订单状态表
            vo.setStatusLabel(BIZ_STATUS.getOrDefault(num.intValue(),
                    ORDER_STATUS.getOrDefault(num.intValue(), "-")));
        } else if (status instanceof String s) {
            // 凭证状态为字符串（DRAFT/POSTED）
            vo.setStatusLabel(VOUCHER_STATUS.getOrDefault(s, s));
        }
        Object billCount = row.get("billCount");
        vo.setExtra(billCount instanceof Number n ? n.intValue() + " 张出库单" : null);
        return vo;
    }

    /** 对账月份解析：空 = 当前月；非法格式 → 业务异常。返回 [月初, 次月初) 半开区间 */
    private LocalDate[] monthRange(String month) {
        YearMonth ym;
        if (month == null || month.isBlank()) {
            ym = YearMonth.now();
        } else {
            try {
                ym = YearMonth.parse(month.trim());
            } catch (DateTimeParseException e) {
                throw BusinessException.ofKey("erp.report.month-format-ym", month);
            }
        }
        return new LocalDate[]{ym.atDay(1), ym.plusMonths(1).atDay(1)};
    }

    private static PrintDataVO.PrintColumn col(String key, String label, Integer width) {
        return new PrintDataVO.PrintColumn(key, label, width, "left");
    }

    private static PrintDataVO.PrintColumn col(String key, String label, Integer width, String align) {
        return new PrintDataVO.PrintColumn(key, label, width, align);
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    /** 空值兜底：null → 空串（打印物上不留 "null" 字样） */
    private static String nvl(String v) {
        return v == null || v.isBlank() ? "" : v;
    }

    private static int intVal(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    /** Map 值 → BigDecimal（NULL → 0） */
    private static BigDecimal dec(Object v) {
        if (v instanceof BigDecimal d) {
            return d;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 金额：千分位 + 2 位小数（0 → 0.00） */
    private static String fmtMoney(BigDecimal v) {
        return new DecimalFormat("#,##0.00").format(v == null ? BigDecimal.ZERO : v);
    }

    /** 数量：千分位 + 最多 4 位小数去尾零（35.0000 → 35） */
    private static String fmtQty(BigDecimal v) {
        return new DecimalFormat("#,##0.####").format(v == null ? BigDecimal.ZERO : v);
    }

    /** 税率：13.00 → 13（最多 2 位小数去尾零） */
    private static String fmtRate(BigDecimal v) {
        return new DecimalFormat("#,##0.##").format(v == null ? BigDecimal.ZERO : v);
    }
}
