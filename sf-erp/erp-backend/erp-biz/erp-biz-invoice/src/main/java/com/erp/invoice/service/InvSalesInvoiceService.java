package com.erp.invoice.service;

import com.erp.common.result.PageResult;
import com.erp.invoice.dto.form.SalesInvoiceApplyForm;
import com.erp.invoice.dto.request.SalesInvoiceQuery;
import com.erp.invoice.dto.vo.SalesInvoiceVO;
import com.erp.invoice.dto.vo.ToBeInvoicedVO;

import java.util.List;

/**
 * 销项发票服务：开票申请（从销售出库单）→ 开票 → 红冲，及待开票清单查询。
 */
public interface InvSalesInvoiceService {

    /**
     * 分页查询销项发票。
     *
     * @param query    查询条件（客户/状态/关键词/开票日期区间）
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 发票分页（含客户名称）
     */
    PageResult<SalesInvoiceVO> page(SalesInvoiceQuery query, long pageNum, long pageSize);

    /**
     * 发票明细。
     *
     * @param id 发票 ID
     * @return 明细 VO
     * @throws com.erp.common.exception.BusinessException 发票不存在
     */
    SalesInvoiceVO detail(Long id);

    /**
     * 开票申请：从销售出库单生成待开票发票。
     * <p>
     * 读 sal_outstock + sal_outstock_detail 汇总不含税金额（明细缺失回退单头合计），
     * 税率默认 13%；校验出库单已审核且不存在未红冲蓝字发票（防重复开票）。
     *
     * @param outstockId 销售出库单 ID
     * @param form       申请表单（可空：税率缺省 13%、备注可空）
     * @return 新发票 ID（状态=待开票）
     * @throws com.erp.common.exception.BusinessException 出库单不存在/未审核/已开过票/税率非法
     */
    Long applyFromOrder(Long outstockId, SalesInvoiceApplyForm form);

    /**
     * 开票：待开票 → 已开票（写开票日期与开票人）。
     *
     * @param id 发票 ID
     * @throws com.erp.common.exception.BusinessException 发票不存在或状态不允许
     */
    void issue(Long id);

    /**
     * 红冲：原蓝字票置为已红冲，同事务插入负数红字发票（关联原票号）。
     *
     * @param id 原蓝字发票 ID
     * @throws com.erp.common.exception.BusinessException 发票不存在/非已开票状态/红字票不允许再红冲
     */
    void reverse(Long id);

    /**
     * 客户待开票清单（已出库未开票：已审核出库 − 未红冲蓝字发票占用）。
     *
     * @param customerId 客户 ID（null = 全部客户）
     * @return 清单行（含客户名称）
     */
    List<ToBeInvoicedVO> toBeInvoiced(Long customerId);
}
