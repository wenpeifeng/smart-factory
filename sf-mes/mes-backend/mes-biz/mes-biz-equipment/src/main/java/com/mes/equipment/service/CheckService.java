package com.mes.equipment.service;

import com.mes.common.result.PageResult;
import com.mes.equipment.dto.form.CheckForm;
import com.mes.equipment.entity.EqpCheck;

/**
 * 设备点检服务。
 */
public interface CheckService {

    /**
     * 登记点检（NG 时自动把设备置 REPAIR）。
     *
     * @param form 表单
     * @return 记录 id
     */
    Long record(CheckForm form);

    /**
     * 分页查询点检记录。
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @param deviceId 设备过滤
     * @return 分页结果
     */
    PageResult<EqpCheck> page(long pageNum, long pageSize, Long deviceId);
}
