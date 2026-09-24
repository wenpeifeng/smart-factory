package com.erp.mrp.service;

import com.erp.mrp.dto.form.DispatchForm;

/**
 * 派工服务 —— 将待派工的工序任务指派给员工或设备。
 */
public interface MrpDispatchService {

    /**
     * 派工：PENDING_DISPATCH → DISPATCHED，记录派工对象与时间（可顺带调整计划开工时间）。
     *
     * @param woStepId 工序任务ID
     * @param form     派工表单
     * @throws com.erp.common.exception.BusinessException 任务不存在 / 非待派工状态
     */
    void dispatch(Long woStepId, DispatchForm form);
}
