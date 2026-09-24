package com.erp.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.equipment.dto.vo.StatisticsVO;
import com.erp.equipment.entity.EqpDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 设备台账 Mapper。
 * <p>
 * 台账分页按 QueryWrapper 在 Service 组装（条件简单）；此处只承载统计类 SQL。
 */
@Mapper
public interface EqpDeviceMapper extends BaseMapper<EqpDevice> {

    /**
     * 设备状态分布统计（在用台账按 status 计数）。
     *
     * @return 状态计数列表（status + count）
     */
    List<StatisticsVO.StatusCount> countByStatus();

    /**
     * 当月维修次数 TOP5 设备（按工单报修时间归月）。
     *
     * @param monthStart 当月第一天（含）
     * @return 前 5 名设备及其维修次数（不足 5 条按实际返回）
     */
    List<StatisticsVO.DeviceRepairTop> selectMonthRepairTop(@Param("monthStart") LocalDate monthStart);
}
