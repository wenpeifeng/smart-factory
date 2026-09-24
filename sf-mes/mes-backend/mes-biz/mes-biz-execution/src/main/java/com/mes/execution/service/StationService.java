package com.mes.execution.service;

import com.mes.common.result.PageResult;
import com.mes.execution.dto.form.StationReportForm;
import com.mes.execution.dto.query.StationRecordQuery;
import com.mes.execution.dto.vo.StationRecordVO;

/**
 * 过站扫码报工服务。
 * <p>一笔报工事务内完成：落过站记录 → 维护 WIP 汇总 → 回写工序/工单累计；
 * PASS 结果异步语义上还需回传 ERP（推送日志保证幂等+重试，失败不阻断报工）。
 */
public interface StationService {

    /**
     * 过站扫码报工。
     *
     * @param form 报工表单（报工人取当前登录人）
     * @return 过站记录 id
     */
    Long report(StationReportForm form);

    /**
     * 分页查询过站记录。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<StationRecordVO> page(StationRecordQuery query);
}
