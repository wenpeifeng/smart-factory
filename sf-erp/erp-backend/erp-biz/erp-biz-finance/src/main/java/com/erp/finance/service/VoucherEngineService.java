package com.erp.finance.service;

import com.erp.finance.dto.vo.VoucherPreviewVO;

import java.time.LocalDate;

/**
 * 凭证模板引擎服务（V521）：任一业务单据审核后按凭证模板自动生成会计凭证。
 * <p>
 * 八类单据：PUR_INSTOCK 采购入库 / SAL_OUTSTOCK 销售出库 / SAL_RET 销售退货 /
 * PUR_RET 采购退货 / INVOICE_SALES 销项发票 / INVOICE_PURCHASE 进项发票 /
 * RECEIPT 收款 / PAYMENT 付款。
 * <p>
 * 幂等：fin_voucher.source_type + source_id + book_id 定位已生成凭证，
 * 已生成跳过；DB 唯一键 uk_voucher_source 兜底并发。
 */
public interface VoucherEngineService {

    /**
     * 按模板生成凭证（DRAFT）。
     * <p>
     * 组装失败（模板缺分录/科目非法/借贷不平 &gt; ±0.01）抛业务异常不落库；
     * 该单据已生成过凭证时直接返回已有凭证（幂等）。
     *
     * @param bizType 业务类型（八类之一）
     * @param bizId   来源单据 ID
     * @return 生成结果（voucherId + 是否新生成）
     */
    GenerationResult generate(String bizType, Long bizId);

    /**
     * 试算预览：不落库返回将生成的凭证内容（与实际生成同构）。
     *
     * @param bizType 业务类型
     * @param bizId   来源单据 ID
     * @return 预览内容（含模板停用/已生成等状态说明）
     */
    VoucherPreviewVO preview(String bizType, Long bizId);

    /**
     * 扫描八类已生效且无对应凭证的单据并生成凭证（当前账套上下文）。
     * <p>
     * 单张失败只记日志不中断本轮；已生成/模板停用自动跳过。
     *
     * @param bizType  业务类型（null=全部八类）
     * @param from     业务日期起（含，可空）
     * @param to       业务日期止（含，可空）
     * @param daysBack 自动扫描回看窗口天数（可空=不限，与 from/to 取交集）
     * @return 本轮新生成凭证数
     */
    int scan(String bizType, LocalDate from, LocalDate to, Integer daysBack);

    /**
     * 生成结果。
     *
     * @param voucherId   凭证 ID（新生成或已存在的）
     * @param voucherNo   凭证号
     * @param created     true=本轮新生成；false=已存在（幂等跳过）
     * @param skipReason  未新生成的原因（幂等跳过等）
     */
    record GenerationResult(Long voucherId, String voucherNo, boolean created, String skipReason) {
    }
}
