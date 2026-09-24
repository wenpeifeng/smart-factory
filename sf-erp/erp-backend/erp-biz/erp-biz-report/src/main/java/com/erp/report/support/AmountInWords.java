package com.erp.report.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 人民币金额大写转换工具（打印中心专用）。
 * <p>
 * 规则（银行/金蝶单据口径）：
 * <ul>
 *   <li>数字部分：壹贰叁肆伍陆柒捌玖；单位：拾佰仟万亿；金额单位：元，角分两位小数；</li>
 *   <li>整数部分按 4 位一组（万亿/亿/万/元）读取，组内连续零只读一个"零"，
 *       组边界不足仟位的低组前补"零"（如 10500 → 壹万零伍佰；105000 → 壹拾万伍仟）；</li>
 *   <li>小数部分：0.00 → "整"；x.50 → "…元伍角"；x.05 → "…元零伍分"；x.56 → "…元伍角陆分"；</li>
 *   <li>负数加"负"前缀；0 → "零元整"。</li>
 * </ul>
 * 例：1234.56 → 壹仟贰佰叁拾肆元伍角陆分；100.00 → 壹佰元整。
 */
public final class AmountInWords {

    /** 中文大写数字（下标即数字值） */
    private static final String[] DIGITS = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
    /** 组内单位（个/拾/佰/仟），下标 = 位数（从低到高） */
    private static final String[] GROUP_UNITS = {"", "拾", "佰", "仟"};
    /** 组间单位：元(个位组)/万/亿/万亿 */
    private static final String[] SECTION_UNITS = {"", "万", "亿", "万亿"};

    private AmountInWords() {
    }

    /**
     * 金额转人民币大写。
     *
     * @param amount 金额（DECIMAL(18,2) 口径），null 按零处理
     * @return 大写金额，如 "壹仟贰佰叁拾肆元伍角陆分"、"壹佰元整"
     */
    public static String toWords(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        // 统一保留 2 位小数（超过 2 位的尾差四舍五入，避免取整口径不一致）
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        boolean negative = normalized.signum() < 0;
        BigDecimal abs = normalized.abs();

        long fenTotal = abs.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        long yuan = fenTotal / 100;
        int jiao = (int) (fenTotal / 10 % 10);
        int fen = (int) (fenTotal % 10);

        StringBuilder sb = new StringBuilder();
        if (negative) {
            sb.append('负');
        }
        if (yuan == 0 && jiao == 0 && fen == 0) {
            return sb.append("零元整").toString();
        }
        sb.append(integerToChinese(yuan)).append('元');
        if (jiao == 0 && fen == 0) {
            sb.append('整');
        } else {
            if (jiao > 0) {
                sb.append(DIGITS[jiao]).append('角');
            } else if (fen > 0) {
                sb.append('零'); // 如 100.05 → 壹佰元零伍分
            }
            if (fen > 0) {
                sb.append(DIGITS[fen]).append('分');
            }
        }
        return sb.toString();
    }

    /**
     * 整数部分转大写（不含"元"）。0 → "零"。
     * 按 4 位一组拆组后从高到低直接拼装；组边界按银行口径补零：
     * 与上一打印组之间，若中间有整组零被跳过、或本组千位为 0（不足仟位），则补一个"零"。
     */
    private static String integerToChinese(long value) {
        if (value == 0) {
            return DIGITS[0];
        }
        // 低 → 高拆组（每组 0~9999），sections[0] 为个位组
        List<Integer> sections = new ArrayList<>();
        long rest = value;
        while (rest > 0) {
            sections.add((int) (rest % 10000));
            rest /= 10000;
        }
        // 从高到低顺序拼装
        StringBuilder sb = new StringBuilder();
        int lastPrinted = -1;
        for (int level = sections.size() - 1; level >= 0; level--) {
            String reading = sectionToChinese(sections.get(level));
            if (reading.isEmpty()) {
                continue; // 整组为 0000，不读，但可能造成"跳组零"
            }
            if (lastPrinted >= 0) {
                boolean gapSkipped = lastPrinted - level > 1;          // 中间有整组零
                boolean lowBelowThousand = sections.get(level) < 1000; // 本组千位为 0
                if (gapSkipped || lowBelowThousand) {
                    sb.append(DIGITS[0]);
                }
            }
            sb.append(reading).append(SECTION_UNITS[Math.min(level, SECTION_UNITS.length - 1)]);
            lastPrinted = level;
        }
        return sb.toString();
    }

    /**
     * 单组（0~9999）转大写：组内连续零合并为一个"零"，末尾零不读。全零返回空串。
     */
    private static String sectionToChinese(int section) {
        StringBuilder sb = new StringBuilder();
        boolean pendingZero = false;
        int divisor = 1000;
        for (int pos = 3; pos >= 0; pos--) {
            int digit = section / divisor % 10;
            divisor /= 10;
            if (digit == 0) {
                if (sb.length() > 0) {
                    pendingZero = true;
                }
                continue;
            }
            if (pendingZero) {
                sb.append(DIGITS[0]);
                pendingZero = false;
            }
            sb.append(DIGITS[digit]).append(GROUP_UNITS[pos]);
        }
        return sb.toString();
    }
}
