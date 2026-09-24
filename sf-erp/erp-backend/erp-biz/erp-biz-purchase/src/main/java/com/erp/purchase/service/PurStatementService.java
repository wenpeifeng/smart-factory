package com.erp.purchase.service;

import com.erp.purchase.dto.vo.SupplierStatementVO;

/**
 * 供应商对账单 Service（只读统计）。
 */
public interface PurStatementService {

    /**
     * 按供应商+月份汇总：已审核采购入库金额、已审核退货金额、净额（入库-退货）。
     *
     * @param supplierId 供应商 ID
     * @param month      对账月份，格式 yyyy-MM
     * @return 对账汇总 VO
     * @throws com.erp.common.exception.BusinessException 供应商不存在或月份格式非法
     */
    SupplierStatementVO statement(Long supplierId, String month);
}
