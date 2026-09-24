package com.mes.plan.service;

import com.mes.plan.dto.vo.WoStepVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 工单工序任务服务（派工与过站累计，供 execution 模块调用与前端派工页使用）。
 */
public interface WoStepService {

    /**
     * 派工：PENDING_DISPATCH → DISPATCHED，指定派工对象。
     *
     * @param stepId      工序任务 id
     * @param assigneeId  派工对象用户 id
     * @param assigneeName 派工对象姓名
     */
    void dispatch(Long stepId, Long assigneeId, String assigneeName);

    /**
     * 过站累计：DISPATCHED → PROCESSING，累计合格/报废/返修数与实际工时；
     * 累计合格+报废+返修达到计划数量时工序完工（→ FINISHED）。
     *
     * @param stepId      工序任务 id
     * @param goodQty     本笔合格数
     * @param scrapQty    本笔报废数
     * @param reworkQty   本笔返修数
     * @param workMinutes 本笔工时（分钟）
     */
    void accumulateStation(Long stepId, BigDecimal goodQty, BigDecimal scrapQty,
                           BigDecimal reworkQty, Integer workMinutes);

    /**
     * 查询工单的全部工序任务。
     *
     * @param workorderId 工单 id
     * @return 工序列表（按序号升序）
     */
    List<WoStepVO> listByWorkorder(Long workorderId);

    /**
     * 按 id 查询工序任务。
     *
     * @param stepId 工序任务 id
     * @return VO
     */
    WoStepVO getById(Long stepId);
}
