package com.erp.report.support;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 报表 CSV 导出工具（仅报表中心使用）。
 * <p>
 * 关键约定：
 * <ul>
 *   <li>Content-Type: text/csv;charset=UTF-8，Content-Disposition 附件（RFC 6266）；</li>
 *   <li>首行表头；写入 UTF-8 BOM（\uFEFF），保证 Excel 直接双击打开中文不乱码；</li>
 *   <li>字段含逗号/引号/换行时按 RFC 4180 用双引号包裹并把内部引号翻倍；</li>
 *   <li>统一 CRLF 行尾（Excel/Numbers 兼容）。</li>
 * </ul>
 */
public final class CsvExporter {

    private CsvExporter() {
    }

    /**
     * 写出 CSV 附件。
     *
     * @param response HTTP 响应（由调用方确保在写 JSON 之前进入本方法）
     * @param filename 附件文件名（ASCII，避免编码兼容问题）
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

    /** 生成报表附件名：{prefix}_{groupBy}_{yyyyMMdd}.csv */
    public static String filename(String prefix, String groupBy) {
        return prefix + "_" + groupBy + "_" + java.time.LocalDate.now().toString().replace("-", "") + ".csv";
    }

    /** null → 空串（CSV 单元格值） */
    public static String nvl(String value) {
        return value == null ? "" : value;
    }

    /** BigDecimal → 明文串（不用科学计数法），null → 空串 */
    public static String plain(java.math.BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    /** RFC 4180 转义：含逗号/引号/换行的字段加引号包裹，内部引号翻倍 */
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
