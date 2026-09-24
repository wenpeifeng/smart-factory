package com.mes.trace.service;

import com.mes.trace.dto.vo.TraceNodeVO;

import java.util.List;

/**
 * 正反向追溯查询服务。
 */
public interface TraceService {

    /**
     * 正向追溯：来料批次/关键件 SN → 消耗到了哪些工单产出（下游产品）。
     *
     * @param componentNo 批次号或 SN
     * @return 下游节点列表
     */
    List<TraceNodeVO> traceForward(String componentNo);

    /**
     * 反向追溯：产品批次/SN → 用了哪些来料批次/关键件 SN。
     *
     * @param productNo 产品批次号或 SN
     * @return 上游节点列表
     */
    List<TraceNodeVO> traceBackward(String productNo);
}
