package com.erp.mrp.service;

import com.erp.mrp.dto.form.WorkReportForm;
import com.erp.mrp.dto.vo.WorkReportVO;

import java.util.List;

/**
 * 工序报工服务 —— 报工明细落库并回写工单工序任务状态。
 */
public interface MrpWorkReportService {

    /**
     * 工序报工：
     * <ul>
     *   <li>写 mrp_work_report 明细（bad &gt; 0 时记录质量待处理说明，QC 挂点）；</li>
     *   <li>回写 mrp_wo_step：累计合格/不合格/实际工时；首个报工 → 加工中；
     *       累计合格达到计划数量 → 已完工；</li>
     *   <li>全部工序完工不阻塞一期完工入库流程（仅提供进度视图）。</li>
     * </ul>
     *
     * @param form 报工表单
     * @return 报工明细ID
     * @throws com.erp.common.exception.BusinessException 任务不存在 / 已完工 / 合格+不合格为0 / 累计合格将超计划数量
     */
    Long report(WorkReportForm form);

    /**
     * 报工明细查询（woStepId / workOrderId 二选一过滤，都空返回最近记录）。
     *
     * @param woStepId    工序任务ID，可空
     * @param workOrderId 生产订单ID，可空
     * @return 报工明细列表（时间倒序，最多200条）
     */
    List<WorkReportVO> list(Long woStepId, Long workOrderId);
}
