package com.erp.equipment.service;

import com.erp.common.result.PageResult;
import com.erp.equipment.dto.form.DeviceForm;
import com.erp.equipment.dto.form.DeviceStatusForm;
import com.erp.equipment.dto.request.DeviceQuery;
import com.erp.equipment.dto.vo.DeviceVO;

/**
 * 设备台账 Service —— 设备主数据的增删改查与状态流转。
 */
public interface EqpDeviceService {

    /**
     * 台账分页查询。
     *
     * @param query 查询条件（状态/编号名称型号关键词）
     * @return 分页结果
     */
    PageResult<DeviceVO> page(DeviceQuery query);

    /**
     * 台账详情。
     *
     * @param id 设备 ID
     * @return 设备 VO
     * @throws com.erp.common.exception.BusinessException 设备不存在
     */
    DeviceVO detail(Long id);

    /**
     * 新增设备（编号留空自动生成 SB+日期+流水）。
     *
     * @param form 设备表单
     * @return 新设备 ID
     * @throws com.erp.common.exception.BusinessException 编号已存在
     */
    Long create(DeviceForm form);

    /**
     * 修改设备（编号以库内为准不可改）。
     *
     * @param id   设备 ID
     * @param form 设备表单
     */
    void update(Long id, DeviceForm form);

    /**
     * 删除设备（逻辑删除）。存在未完结工单（待维修/维修中）时拒绝。
     *
     * @param id 设备 ID
     */
    void delete(Long id);

    /**
     * 设备状态流转（报废单向终态，其余三态互通）。
     *
     * @param id   设备 ID
     * @param form 目标状态
     * @throws com.erp.common.exception.BusinessException 非法流转（如报废→运行）
     */
    void changeStatus(Long id, DeviceStatusForm form);
}
