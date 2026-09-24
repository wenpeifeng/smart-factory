package com.erp.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.equipment.entity.EqpInvMovement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存流水写入 Mapper —— 设备模块对约定表 inv_movement 的唯一写入口。
 * <p>
 * 只 INSERT（EQP_OUT 备件出库 / 预留 EQP_IN 备件退回），不更新不删除；
 * 流水是不可变的审计事实，与库存变更同事务（见 EqpRepairServiceImpl）。
 */
@Mapper
public interface EqpInvMovementMapper extends BaseMapper<EqpInvMovement> {
}
