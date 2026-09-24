package com.erp.inventory.service;

import com.erp.inventory.dto.form.TransferForm;

/**
 * 仓库调拨服务（即时调拨，多行一个事务、共用一个调拨单号）。
 * <p>
 * 单行调拨能力由 {@link StockService#transfer} 提供；
 * 本服务面向页面操作：一次提交多行物料，全部成功或全部回滚。
 */
public interface StockTransferService {

    /**
     * 执行整单调拨：生成一个 TR 调拨单号，逐行调拨（一出一入），
     * 任一行库存不足则整单回滚。
     *
     * @param form 调拨表单（调出仓/调入仓/多行物料数量）
     * @return 调拨单号（流水页可按此号配对一出一入两条 MOVE 流水）
     * @throws com.erp.common.exception.BusinessException 两仓相同 / 任一行库存不足
     */
    String transfer(TransferForm form);
}
