package com.erp.invoice.service;

import com.erp.common.result.PageResult;
import com.erp.invoice.dto.form.PurchaseInvoiceRegisterForm;
import com.erp.invoice.dto.request.PurchaseInvoiceQuery;
import com.erp.invoice.dto.vo.PurchaseInvoiceVO;
import com.erp.invoice.dto.vo.ToBeRegisteredVO;

import java.util.List;

/**
 * 进项发票服务：从采购入库单登记 → 认证 → 红冲，及待登记清单查询。
 */
public interface InvPurchaseInvoiceService {

    /**
     * 分页查询进项发票。
     *
     * @param query    查询条件（供应商/状态/关键词/开票日期区间）
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 发票分页（含供应商名称）
     */
    PageResult<PurchaseInvoiceVO> page(PurchaseInvoiceQuery query, long pageNum, long pageSize);

    /**
     * 发票明细。
     *
     * @param id 发票 ID
     * @return 明细 VO
     * @throws com.erp.common.exception.BusinessException 发票不存在
     */
    PurchaseInvoiceVO detail(Long id);

    /**
     * 登记：从采购入库单生成待认证进项发票。
     * <p>
     * 税率缺省取入库明细唯一非 0 税率，否则 13%；发票号码优先使用票面号（8 位数字），
     * 缺省系统取号；校验入库单已审核且不存在未红冲蓝字发票。
     *
     * @param instockId 采购入库单 ID
     * @param form      登记表单（可空）
     * @return 新发票 ID（状态=待认证）
     * @throws com.erp.common.exception.BusinessException 入库单不存在/未审核/已登记过/税率非法
     */
    Long registerFromInstock(Long instockId, PurchaseInvoiceRegisterForm form);

    /**
     * 认证：待认证 → 已认证（写认证日期与认证人；税额自此进入税负统计）。
     *
     * @param id 发票 ID
     * @throws com.erp.common.exception.BusinessException 发票不存在或状态不允许
     */
    void authenticate(Long id);

    /**
     * 红冲：原蓝字票置为已红冲，同事务插入负数红字发票（红字票直接已认证，
     * 认证日期取红冲当日，使已认证进项税额自然冲减）。
     *
     * @param id 原蓝字发票 ID
     * @throws com.erp.common.exception.BusinessException 发票不存在/非已认证状态/红字票不允许再红冲
     */
    void reverse(Long id);

    /**
     * 供应商待登记清单（已审核入库 − 未红冲蓝字发票占用）。
     *
     * @param supplierId 供应商 ID（null = 全部供应商）
     * @return 清单行（含供应商名称）
     */
    List<ToBeRegisteredVO> toBeRegistered(Long supplierId);
}
