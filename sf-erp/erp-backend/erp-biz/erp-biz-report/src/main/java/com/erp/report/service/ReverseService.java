package com.erp.report.service;

import com.erp.report.dto.vo.ReverseResultVO;

/**
 * 通用红冲服务：任意"已审核"单据生成一张负数反向单（单号=原单号+R），
 * 红冲单落库即完成反向过账（库存/成本/应收应付/账户余额），原单标记 REVERSED。
 * <p>
 * 支持类型（DocTypes.REVERSIBLE）：pur_instock / sal_outstock / pur_return /
 * sal_return / fin_receipt / fin_payment。配对关系落 biz_reverse_log。
 */
public interface ReverseService {

    /**
     * 发起红冲（同事务完成：红冲单生成 + 反向过账 + 原单标记）。
     *
     * @param docType 单据类型（DocTypes 常量）
     * @param id      原单 ID
     * @param reason  红冲原因（审计留痕）
     */
    ReverseResultVO reverse(String docType, Long id, String reason);
}
