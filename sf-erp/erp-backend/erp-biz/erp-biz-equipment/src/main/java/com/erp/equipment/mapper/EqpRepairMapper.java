package com.erp.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.equipment.dto.request.RepairQuery;
import com.erp.equipment.dto.vo.RepairVO;
import com.erp.equipment.entity.EqpRepair;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 维修工单 Mapper。分页走 XML（JOIN 设备台账 + 备件成本合计子查询）。
 */
@Mapper
public interface EqpRepairMapper extends BaseMapper<EqpRepair> {

    /**
     * 维修工单分页（JOIN eqp_device 带出设备信息，子查询聚合备件成本参考合计）。
     *
     * @param page  MP 分页对象
     * @param query 查询条件（status/deviceId/keyword 工单号或设备名）
     * @return 工单分页结果
     */
    IPage<RepairVO> selectRepairPage(IPage<RepairVO> page, @Param("q") RepairQuery query);

    /**
     * 当月备件消耗成本：SUM(完工工单的备件明细 cost_ref)。
     *
     * @param monthStart 当月第一天（含），按工单 finish_time 归月
     * @return 成本合计（无记录返回 0，不为 null）
     */
    java.math.BigDecimal sumMonthSpareCost(@Param("monthStart") LocalDate monthStart);
}
