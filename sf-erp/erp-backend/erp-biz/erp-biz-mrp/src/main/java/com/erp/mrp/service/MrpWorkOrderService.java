package com.erp.mrp.service;

import com.erp.common.result.PageResult;
import com.erp.mrp.dto.form.FinishForm;
import com.erp.mrp.dto.form.WorkOrderForm;
import com.erp.mrp.dto.vo.WorkOrderVO;
import com.erp.mrp.entity.MrpSuggestion;

/**
 * 生产订单服务 —— 草稿→下达→生产中→完工→关闭 全生命周期。
 */
public interface MrpWorkOrderService {

    /**
     * 分页查询生产订单。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  成品物料编码/名称关键词，可空
     * @param status   状态过滤，可空
     * @return 订单分页（含物料回显）
     */
    PageResult<WorkOrderVO> page(long pageNum, long pageSize, String keyword, String status);

    /**
     * 订单详情。
     *
     * @param id 订单ID
     * @return 订单 VO
     * @throws com.erp.common.exception.BusinessException 不存在
     */
    WorkOrderVO detail(Long id);

    /**
     * 手工新建草稿订单（BOM 自动取成品最新有效版本）。
     *
     * @param form 表单
     * @return 新订单ID
     * @throws com.erp.common.exception.BusinessException 物料不存在 / 无有效BOM
     */
    Long create(WorkOrderForm form);

    /**
     * 修改草稿订单。
     *
     * @param id   订单ID
     * @param form 表单
     * @throws com.erp.common.exception.BusinessException 非草稿状态
     */
    void update(Long id, WorkOrderForm form);

    /**
     * 删除草稿订单。
     *
     * @param id 订单ID
     * @throws com.erp.common.exception.BusinessException 非草稿状态
     */
    void delete(Long id);

    /**
     * 下达订单：DRAFT → RELEASED（校验 BOM 仍有效）。三段式（审批流接入）：
     * 无启用流程/条件全不满足 -> 直接下达；审批中 -> 不执行，返回"已提交审批"；
     * 审批通过后再下达 -> 执行下达；驳回后再下达 -> 重新发起流程（新实例）。
     *
     * @param id 订单ID
     * @return 下达结果提示（"下达成功"/"已提交审批"/"审批已通过，订单下达完成"）
     * @throws com.erp.common.exception.BusinessException 状态不合法 / BOM 已失效
     */
    String release(Long id);

    /**
     * 完工入库：良品经 StockService 批次入库（inv_movement PROD_IN）+ CostService 实时计价，
     * 可分次；良品+不良累计达到订单数量自动置为已完工。
     * <p>
     * QC 卡点（V2101 接线）：工单存在未完成（草稿）检验单时拒绝完工入库。
     *
     * @param id   订单ID
     * @param form 完工表单
     * @throws com.erp.common.exception.BusinessException 状态不合法 / 超出订单数量 /
     *         存在未完成检验单
     */
    void finish(Long id, FinishForm form);

    /**
     * 关闭订单：FINISHED → CLOSED。
     *
     * @param id 订单ID
     * @throws com.erp.common.exception.BusinessException 状态不合法
     */
    void close(Long id);

    /**
     * 由 MRP 生产建议生成草稿订单（建议状态同步置为已转单）。
     *
     * @param suggestion  生产类型建议
     * @param warehouseId 默认入库仓库（可空）
     * @return 新订单ID
     * @throws com.erp.common.exception.BusinessException 建议类型/状态不合法 / 无有效BOM
     */
    Long createFromSuggestion(MrpSuggestion suggestion, Long warehouseId);
}
