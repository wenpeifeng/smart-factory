package com.erp.workflow.support;

import com.erp.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 节点条件表达式求值器（轻量实现）。
 * <p>
 * 语法：{@code 变量名 比较符 数值}，如 {@code amount>10000}、{@code qty >= 50}。
 * 支持比较符：{@code > >= < <= == !=}；数值统一转 BigDecimal 比较（金额/数量同构）。
 * 求值失败（语法错 / 变量缺失 / 变量非数值）一律视为"条件不满足"，即跳过该节点，
 * 并打 WARN 日志便于排查 —— 宁可少卡一节点，不能因表达式写错把单据卡死。
 */
@Slf4j
public final class ConditionEvaluator {

    /** 变量名（字母数字下划线） + 比较符 + 数值（整数/小数/负数），中间允许空白 */
    private static final Pattern EXPR = Pattern.compile(
            "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(>=|<=|==|!=|>|<)\\s*(-?\\d+(?:\\.\\d+)?)\\s*$");

    private ConditionEvaluator() {
    }

    /**
     * 校验条件表达式语法（流程定义保存时调用，写错早暴露）。
     *
     * @param expr 条件表达式（空白视为无条件，直接通过）
     * @throws BusinessException 语法不合法时
     */
    public static void validate(String expr) {
        if (expr == null || expr.isBlank()) {
            return;
        }
        if (!EXPR.matcher(expr).matches()) {
            throw BusinessException.ofKey("erp.workflow.bad-condition-expr", expr);
        }
    }

    /**
     * 求值条件表达式。
     *
     * @param expr 条件表达式（空白/null 视为无条件，恒为 true）
     * @param vars 业务变量（如 {"amount": 8000}）
     * @return 是否满足（求值失败返回 false，即跳过该节点）
     */
    public static boolean matches(String expr, Map<String, Object> vars) {
        if (expr == null || expr.isBlank()) {
            return true;
        }
        Matcher matcher = EXPR.matcher(expr);
        if (!matcher.matches()) {
            log.warn("条件表达式[{}]语法不合法，按不满足处理(跳过节点)", expr);
            return false;
        }
        String varName = matcher.group(1);
        String operator = matcher.group(2);
        BigDecimal target = new BigDecimal(matcher.group(3));

        Object value = vars == null ? null : vars.get(varName);
        if (value == null) {
            log.warn("条件表达式[{}]变量[{}]在流程变量中不存在，按不满足处理(跳过节点)", expr, varName);
            return false;
        }
        BigDecimal actual;
        try {
            actual = value instanceof Number n
                    ? new BigDecimal(n.toString())
                    : new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            log.warn("条件表达式[{}]变量[{}]值[{}]不是数值，按不满足处理(跳过节点)", expr, varName, value);
            return false;
        }
        int cmp = actual.compareTo(target);
        return switch (operator) {
            case ">" -> cmp > 0;
            case ">=" -> cmp >= 0;
            case "<" -> cmp < 0;
            case "<=" -> cmp <= 0;
            case "==" -> cmp == 0;
            case "!=" -> cmp != 0;
            default -> false;
        };
    }
}
