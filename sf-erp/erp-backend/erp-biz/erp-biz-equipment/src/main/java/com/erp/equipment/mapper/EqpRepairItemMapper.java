package com.erp.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.equipment.dto.vo.RepairDetailVO;
import com.erp.equipment.entity.EqpRepairItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 维修工单备件明细 Mapper。
 */
@Mapper
public interface EqpRepairItemMapper extends BaseMapper<EqpRepairItem> {

    /**
     * 按工单查明细行（JOIN base_material 带出编码/名称/参考成本）。
     *
     * @param repairId 工单 ID
     * @return 明细行列表（无行返回空列表）
     */
    List<RepairDetailVO.ItemVO> selectItemsByRepairId(@Param("repairId") Long repairId);
}
