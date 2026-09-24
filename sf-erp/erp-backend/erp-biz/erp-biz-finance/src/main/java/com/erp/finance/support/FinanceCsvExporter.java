package com.erp.finance.support;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 财务三大报表 CSV 导出工具。
 * <p>
 * 与报表中心 CsvExporter 同一套约定（erp-biz-finance 不依赖 erp-biz-report，
 * 依赖方向只允许 report → finance，故在本模块内实现）：
 * <ul>
 *   <li>Content-Type: text/csv;charset=UTF-8，Content-Disposition 附件（RFC 6266）；</li>
 *   <li>首行表头；写入 UTF-8 BOM（\uFEFF），Excel 双击打开中文不乱码；</li>
 *   <li>字段含逗号/引号/换行按 RFC 4180 双引号包裹、内部引号翻倍；</li>
 *   <li>统一 CRLF 行尾。</li>
 * </ul>
 */
public final class FinanceCsvExporter {

    private FinanceCsvExporter() {
    }

    /**
     * 写出 CSV 附件。
     *
     * @param response HTTP 响应（在返回 JSON 之前进入本方法）
     * @param filename 附件文件名（ASCII）
     * @param headers  表头（首行）
     * @param rows     数据行（与表头等宽，null 字段输出空串）
     * @throws IOException 写出失败
     */
    public static void export(HttpServletResponse response, String filename,
                              List<String> headers, List<List<String>> rows) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write('\ufeff'); // BOM：Excel 识别 UTF-8
            writeLine(writer, headers);
            for (List<String> row : rows) {
                writeLine(writer, row);
            }
        }
    }

    /** 生成报表附件名：{prefix}_{tag}_{yyyyMMdd}.csv */
    public static String filename(String prefix, String tag) {
        return prefix + "_" + tag + "_" + java.time.LocalDate.now().toString().replace("-", "") + ".csv";
    }

    /** null → 空串 */
    public static String nvl(String value) {
        return value == null ? "" : value;
    }

    /** BigDecimal → 明文串（无科学计数法，金额统一 2 位 HALF_UP），null → 空串 */
    public static String plain(java.math.BigDecimal value) {
        return value == null ? "" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /** 占比 BigDecimal → 明文串（保持原精度），null → 空串 */
    public static String plainRaw(java.math.BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    /** 写一行并追加 CRLF */
    private static void writeLine(Writer writer, List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escape(cells.get(i)));
        }
        writer.write("\r\n");
    }

    /** RFC 4180 转义 */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needQuote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!needQuote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
