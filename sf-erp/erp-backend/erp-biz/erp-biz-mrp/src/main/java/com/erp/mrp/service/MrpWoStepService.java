package com.erp.mrp.service;

import com.erp.mrp.dto.vo.DispatchBoardVO;
import com.erp.mrp.dto.vo.WoStepVO;
import com.erp.mrp.entity.MrpWorkOrder;

import java.util.List;

/**
 * 工单工序任务服务 —— 生产订单联动生成、派工看板、按订单查看工序进度。
 */
public interface MrpWoStepService {

    /**
     * 生产订单下达联动：成品存在已审核工艺路线时按工序序列顺排生成工单工序任务。
     * 无路线则静默跳过（不影响一期流程）；已生成过则幂等跳过。
     * <p>
     * 顺排规则：首道工序计划开始 = 订单计划开工日（空取当天）08:00，
     * 之后每道 = 前置工序计划结束；计划结束 = 计划开始 + 单件工时 × 订单数量（分钟向上取整）。
     *
     * @param wo 已下达的生产订单
     */
    void generateForWorkOrder(MrpWorkOrder wo);

    /**
     * 派工报工看板：按工作中心分组的工序任务（未指定工作中心的任务归入"未分组"）。
     *
     * @param status       状态过滤（PENDING_DISPATCH/DISPATCHED/PROCESSING/FINISHED），可空
     * @param workCenterId 工作中心过滤，可空
     * @return 按工作中心分组的看板
     */
    DispatchBoardVO board(String status, Long workCenterId);

    /**
     * 按生产订单查工序任务（订单工序进度视图）。
     *
     * @param workOrderId 生产订单ID
     * @return 工序任务列表（按序号升序）
     */
    List<WoStepVO> listByWorkOrder(Long workOrderId);
}
