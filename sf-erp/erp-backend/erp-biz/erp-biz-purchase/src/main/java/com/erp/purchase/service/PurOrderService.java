package com.erp.purchase.service;

import com.erp.common.result.PageResult;
import com.erp.purchase.dto.form.PurOrderForm;
import com.erp.purchase.dto.query.PurOrderQuery;
import com.erp.purchase.dto.vo.PurOrderVO;

/**
 * 采购订单 Service。
 * <p>
 * 状态机（只允许正向流转）：0草稿 -> 10已审核 -> 20部分入库 -> 30已完成；
 * 10/20 可人工关闭为 90已关闭（终态）。修改/删除仅限草稿。
 */
public interface PurOrderService {

    /**
     * 分页查询采购订单（单号模糊/供应商/状态/日期区间）。
     *
     * @param query 查询条件
     * @return 订单分页（含供应商/仓库名称，不含明细）
     */
    PageResult<PurOrderVO> page(PurOrderQuery query);

    /**
     * 订单详情（主表 + 明细，含物料编码/名称/规格/单位）。
     *
     * @param id 订单 ID
     * @return 订单详情
     * @throws com.erp.common.exception.BusinessException 订单不存在
     */
    PurOrderVO detail(Long id);

    /**
     * 新增采购订单（草稿）。单号自动生成，金额服务端重算。
     * <p>
     * 供应商可空：MRP 建议转单（erp-biz-mrp 调用）等场景生成草稿时供应商待定，
     * 用户补填后再审核（audit 前强校验非空）。
     *
     * @param form 订单表单
     * @return 新订单 ID
     */
    Long create(PurOrderForm form);

    /**
     * 修改采购订单（仅草稿可改，明细整体重写）。
     *
     * @param id   订单 ID
     * @param form 订单表单
     */
    void update(Long id, PurOrderForm form);

    /**
     * 审核订单（三段式，审批流接入）：
     * 无启用流程/条件全不满足 -> 直接 0草稿 -> 10已审核；
     * 审批中 -> 不执行审核，返回"已提交审批"；审批通过后再审 -> 执行原审核；
     * 驳回后再审 -> 重新发起流程（新实例）。
     *
     * @param id 订单 ID
     * @return 审核结果提示（"审核成功"/"已提交审批"/"审批已通过，订单审核完成"）
     */
    String audit(Long id);

    /**
     * 关闭订单：10已审核/20部分入库 -> 90已关闭（终态）；
     * 已发起过审批流的草稿（被驳回或审批中）也允许关闭作废。
     *
     * @param id 订单 ID
     */
    void close(Long id);

    /**
     * 删除订单（仅草稿，逻辑删除主表+明细）。
     *
     * @param id 订单 ID
     */
    void delete(Long id);

    /**
     * 【模块内部使用】按明细累计入库数量推进订单进度状态
     * （全部入库->30已完成，否则 20部分入库；退货审核后允许 30->20 回落）。
     * 由入库/退货审核事务调用，不对外暴露 HTTP 接口。
     *
     * @param orderId 订单 ID
     */
    void refreshProgressStatus(Long orderId);
}
