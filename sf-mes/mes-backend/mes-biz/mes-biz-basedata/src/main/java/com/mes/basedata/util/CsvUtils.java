package com.mes.basedata.util;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV 读写工具（basedata 模块内部使用，用于物料导入导出）。
 * <p>
 * 遵循 RFC 4180 子集：字段含逗号/双引号/换行时用双引号包裹，内部双引号转义为两个；
 * 导出统一写 UTF-8 BOM 头，保证 Excel 直接打开不乱码。
 */
public final class CsvUtils {

    /** UTF-8 BOM，Excel 识别中文 CSV 用 */
    public static final String BOM = "\uFEFF";

    private CsvUtils() {
    }

    /**
     * 将一行字段按 RFC 4180 规则转义并拼接。
     *
     * @param fields 字段值（null 按空串处理）
     * @return 单行 CSV 文本（不含换行符）
     */
    public static String escapeLine(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String field = fields.get(i) == null ? "" : fields.get(i);
            if (field.indexOf(',') >= 0 || field.indexOf('"') >= 0 || field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0) {
                sb.append('"').append(field.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(field);
            }
        }
        return sb.toString();
    }

    /**
     * 解析单行 CSV（支持引号包裹与转义）。
     *
     * @param line 单行文本（不含行尾换行）
     * @return 字段列表；空行返回空列表
     */
    public static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    // 连续两个双引号 = 转义的字面引号
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields;
    }
}
