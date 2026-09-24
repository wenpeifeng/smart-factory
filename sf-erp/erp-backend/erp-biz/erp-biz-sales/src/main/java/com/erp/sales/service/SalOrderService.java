package com.erp.sales.service;

import com.erp.common.result.PageResult;
import com.erp.sales.dto.form.SalOrderForm;
import com.erp.sales.dto.vo.SalOrderDetailBundle;
import com.erp.sales.dto.vo.SalOrderVO;

/**
 * 销售订单 Service。
 * <p>
 * 状态机：0草稿 →(审核，含信用额度校验)→ 10已审核 →(出库回写)→ 20部分出库/30已完成；
 * 10/20 →(关闭)→ 90已关闭；草稿可修改/删除。
 */
public interface SalOrderService {

    /**
     * 分页查询销售订单。
     *
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @param keyword  关键词（订单号/客户名称模糊匹配，可空）
     * @param status   状态（可空）
     * @return 订单分页
     */
    PageResult<SalOrderVO> page(long pageNum, long pageSize, String keyword, Integer status);

    /**
     * 查询订单详情（表头 + 明细行）。
     *
     * @param id 订单 ID
     * @return 详情聚合 VO
     * @throws com.erp.common.exception.BusinessException 订单不存在
     */
    SalOrderDetailBundle getDetail(Long id);

    /**
     * 新增销售订单（草稿态）。
     *
     * @param form 订单表单
     * @return 新订单 ID
     * @throws com.erp.common.exception.BusinessException 客户/仓库/物料不存在
     */
    Long create(SalOrderForm form);

    /**
     * 修改销售订单（仅草稿可改，明细整体替换）。
     *
     * @param id   订单 ID
     * @param form 订单表单
     * @throws com.erp.common.exception.BusinessException 订单不存在或非草稿态
     */
    void update(Long id, SalOrderForm form);

    /**
     * 审核销售订单（含客户信用额度校验 + 审批流三段式）。
     * <p>
     * 校验公式：信用额度 − 未收回应收余额 − 本单金额 ≥ 0，否则拒绝。
     * 三段式：无启用流程/条件全不满足 -> 直接 0草稿→10已审核；审批中 -> 不执行审核，
     * 返回"已提交审批"；审批通过后再审 -> 执行原审核；驳回后再审 -> 重新发起流程。
     *
     * @param id 订单 ID
     * @return 审核结果提示（"审核成功"/"已提交审批"/"审批已通过，订单审核完成"）
     * @throws com.erp.common.exception.BusinessException 非草稿态 / 信用额度不足
     */
    String audit(Long id);

    /**
     * 关闭销售订单（已审核/部分出库可关闭，剩余数量不再执行出库；
     * 已发起过审批流的草稿——被驳回或审批中——也允许关闭作废）。
     *
     * @param id 订单 ID
     * @throws com.erp.common.exception.BusinessException 状态不允许关闭
     */
    void close(Long id);

    /**
     * 删除销售订单（仅草稿可删，逻辑删除，含明细行）。
     *
     * @param id 订单 ID
     * @throws com.erp.common.exception.BusinessException 订单不存在或非草稿态
     */
    void delete(Long id);
}
