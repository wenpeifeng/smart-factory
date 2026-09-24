package com.erp.finance.service;

import com.erp.common.result.PageResult;
import com.erp.finance.dto.form.PayableForm;
import com.erp.finance.dto.request.PayableQuery;
import com.erp.finance.dto.vo.PayableVO;

/**
 * 应付单服务：分页查询 / 明细 / 手工补录（与应收对称）。
 * <p>
 * 应付单的主要来源是采购模块入库后直接 INSERT；核销动作在付款单服务内完成。
 */
public interface FinPayableService {

    /**
     * 分页查询应付单。
     *
     * @param query    查询条件（供应商/状态/月份/单号/仅未核销）
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 应付分页（供应商名称已回填）
     */
    PageResult<PayableVO> page(PayableQuery query, long pageNum, long pageSize);

    /**
     * 应付单明细。
     *
     * @param id 应付单 ID
     * @return 应付 VO
     * @throws com.erp.common.exception.BusinessException 单据不存在
     */
    PayableVO detail(Long id);

    /**
     * 手工补录应付单（状态 UNSETTLED）。
     *
     * @param form 应付表单
     * @return 新建应付单 ID
     */
    Long create(PayableForm form);
}
