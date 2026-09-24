package com.erp.finance.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 凭证试算预览 VO（GET /finance/voucher-engine/preview，V521）。
 * <p>
 * 不落库：展示按当前模板将生成的凭证内容；与实际生成结果同构
 * （同一次组装逻辑，仅跳过持久化与单号占用）。
 *
 * @param bizType           业务类型
 * @param bizId             来源单据 ID
 * @param docNo             来源单号
 * @param voucherDate       凭证日期（单据业务日期）
 * @param word              凭证字（收/付/转）
 * @param red               是否红字凭证（负数分录）
 * @param status            预览状态：WILL_GENERATE 可生成 / ALREADY_GENERATED 已生成 /
 *                          TEMPLATE_DISABLED 模板停用 / NO_TEMPLATE 无模板
 * @param existingVoucherId 已生成凭证 ID（status=ALREADY_GENERATED 时非空）
 * @param existingVoucherNo 已生成凭证号
 * @param totalDebit        借方合计（预览分录）
 * @param totalCredit       贷方合计（预览分录）
 * @param entries           预览分录行
 * @param message           提示信息（模板停用/已生成等场景的说明）
 */
public record VoucherPreviewVO(String bizType, Long bizId, String docNo, LocalDate voucherDate,
                               String word, boolean red, String status,
                               Long existingVoucherId, String existingVoucherNo,
                               BigDecimal totalDebit, BigDecimal totalCredit,
                               List<VoucherEntryVO> entries, String message) {

    /** 预览状态：可生成 */
    public static final String WILL_GENERATE = "WILL_GENERATE";
    /** 预览状态：该单据已生成过凭证（幂等跳过） */
    public static final String ALREADY_GENERATED = "ALREADY_GENERATED";
    /** 预览状态：模板已停用（扫描器不再生成） */
    public static final String TEMPLATE_DISABLED = "TEMPLATE_DISABLED";
    /** 预览状态：该类型无模板 */
    public static final String NO_TEMPLATE = "NO_TEMPLATE";
}
