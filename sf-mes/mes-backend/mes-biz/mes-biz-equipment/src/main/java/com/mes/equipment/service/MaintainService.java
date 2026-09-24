package com.mes.equipment.service;

import com.mes.common.result.PageResult;
import com.mes.equipment.dto.form.MaintainForm;
import com.mes.equipment.entity.EqpMaintain;

/**
 * 设备保养/维修服务。
 */
public interface MaintainService {

    /**
     * 创建保养/维修单（PLAN）。
     *
     * @param form 表单
     * @return 单据 id
     */
    Long create(MaintainForm form);

    /**
     * 完成保养/维修（PLAN→DONE，设备状态恢复 IDLE）。
     *
     * @param id              单据 id
     * @param maintainer      执行人
     * @param durationMinutes 耗时分钟
     */
    void finish(Long id, String maintainer, Integer durationMinutes);

    /**
     * 分页查询。
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @param deviceId 设备过滤
     * @param type     PM/REPAIR
     * @param status   PLAN/DONE
     * @return 分页结果
     */
    PageResult<EqpMaintain> page(long pageNum, long pageSize, Long deviceId, String type, String status);
}
