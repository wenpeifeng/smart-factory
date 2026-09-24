package com.mes.equipment.service;

import com.mes.common.result.PageResult;
import com.mes.equipment.dto.form.DeviceForm;
import com.mes.equipment.dto.vo.DeviceVO;

/**
 * 设备台账服务。
 */
public interface DeviceService {

    /**
     * 分页查询设备。
     *
     * @param pageNum      页码
     * @param pageSize     页大小
     * @param keyword      编码/名称/型号模糊
     * @param workcenterId 工作中心过滤
     * @param status       状态过滤
     * @return 分页结果
     */
    PageResult<DeviceVO> page(long pageNum, long pageSize, String keyword, Long workcenterId, String status);

    /**
     * 创建设备（编码 SB+日期流水）。
     *
     * @param form 表单
     * @return 设备 id
     */
    Long create(DeviceForm form);

    /**
     * 修改设备。
     *
     * @param id   设备 id
     * @param form 表单
     */
    void update(Long id, DeviceForm form);

    /**
     * 删除设备。
     *
     * @param id 设备 id
     */
    void delete(Long id);

    /**
     * 变更设备状态（运行/闲置/停机/维修）。
     *
     * @param id     设备 id
     * @param status 目标状态
     */
    void changeStatus(Long id, String status);
}
