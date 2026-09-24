package com.erp.finance.service;

import com.erp.common.result.PageResult;
import com.erp.finance.dto.form.PaymentForm;
import com.erp.finance.dto.request.PaymentQuery;
import com.erp.finance.dto.vo.PaymentDetailVO;
import com.erp.finance.dto.vo.PaymentVO;

/**
 * 付款单服务：付款核销应付（与收款单完全对称）。
 * <p>
 * 额外约束：付款后资金账户余额不得为负（条件 UPDATE 兜底，余额不足整体回滚）。
 */
public interface FinPaymentService {

    /**
     * 分页查询付款单。
     *
     * @param query    查询条件（供应商/月份/单号）
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 付款单分页
     */
    PageResult<PaymentVO> page(PaymentQuery query, long pageNum, long pageSize);

    /**
     * 付款单明细（含核销明细行）。
     *
     * @param id 付款单 ID
     * @return 付款单明细 VO
     * @throws com.erp.common.exception.BusinessException 单据不存在
     */
    PaymentDetailVO detail(Long id);

    /**
     * 保存付款单并核销应付（@Transactional）。
     * <p>
     * 预算控制接线：deptId+subjectId 归属定位到时（表单显式或按供应商最近采购/应付
     * 推导部门）——创建前先预算占用校验（BLOCK 超预算抛异常整体拒绝；WARN 超预算
     * remark 追加提示放行），核销成功后同事务登记预算执行数（PAYMENT）。
     *
     * @param form 付款表单
     * @return 付款单 ID
     * @throws com.erp.common.exception.BusinessException 付款金额与分配额之和不一致 /
     *           核销应付与付款供应商不一致 / 核销额超过未核销余额 / 账户不存在、停用或余额不足 /
     *           预算超限且控制方式为 BLOCK
     */
    Long create(PaymentForm form);
}
