package com.erp.finance.service;

import com.erp.common.result.PageResult;
import com.erp.finance.dto.form.ReceivableForm;
import com.erp.finance.dto.request.ReceivableQuery;
import com.erp.finance.dto.vo.ReceivableVO;

/**
 * 应收单服务：分页查询 / 明细 / 手工补录。
 * <p>
 * 应收单的主要来源是销售模块出库后直接 INSERT（并行协作约定），
 * 本服务作为表 owner 提供查询与补录能力；核销动作在收款单服务内完成。
 */
public interface FinReceivableService {

    /**
     * 分页查询应收单。
     *
     * @param query    查询条件（客户/状态/月份/单号/仅未核销）
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 应收分页（客户名称已回填）
     */
    PageResult<ReceivableVO> page(ReceivableQuery query, long pageNum, long pageSize);

    /**
     * 应收单明细。
     *
     * @param id 应收单 ID
     * @return 应收 VO
     * @throws com.erp.common.exception.BusinessException 单据不存在
     */
    ReceivableVO detail(Long id);

    /**
     * 手工补录应收单（状态 UNSETTLED）。
     *
     * @param form 应收表单
     * @return 新建应收单 ID
     */
    Long create(ReceivableForm form);
}
