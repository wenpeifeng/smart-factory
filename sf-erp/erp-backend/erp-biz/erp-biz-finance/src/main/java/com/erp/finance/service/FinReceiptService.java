package com.erp.finance.service;

import com.erp.common.result.PageResult;
import com.erp.finance.dto.form.ReceiptForm;
import com.erp.finance.dto.request.ReceiptQuery;
import com.erp.finance.dto.vo.ReceiptDetailVO;
import com.erp.finance.dto.vo.ReceiptVO;

/**
 * 收款单服务：收款核销应收。
 * <p>
 * 核销事务内依次完成：校验分配（金额一致/同客户/未超余额）→
 * 条件 UPDATE 回写应收 settled_amount 与状态（PARTIAL/SETTLED）→
 * 资金账户余额原子增加 → 写入收款单主子表 → 记录账户流水。
 * 任一步失败整体回滚。
 */
public interface FinReceiptService {

    /**
     * 分页查询收款单。
     *
     * @param query    查询条件（客户/月份/单号）
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 收款单分页
     */
    PageResult<ReceiptVO> page(ReceiptQuery query, long pageNum, long pageSize);

    /**
     * 收款单明细（含核销明细行）。
     *
     * @param id 收款单 ID
     * @return 收款单明细 VO
     * @throws com.erp.common.exception.BusinessException 单据不存在
     */
    ReceiptDetailVO detail(Long id);

    /**
     * 保存收款单并核销应收（@Transactional）。
     *
     * @param form 收款表单
     * @return 收款单 ID
     * @throws com.erp.common.exception.BusinessException 收款金额与分配额之和不一致 /
     *           核销应收与收款客户不一致 / 核销额超过未核销余额 / 账户不存在或停用
     */
    Long create(ReceiptForm form);
}
