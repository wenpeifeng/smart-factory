package com.erp.hr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.hr.entity.HrPieceWageDetail;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计件工资明细 Mapper（MP 通用 CRUD）。
 * ★ erp-cost 协作契约：本表 (work_order_id, employee_id, amount) 列结构不可变更。
 */
@Mapper
public interface HrPieceWageDetailMapper extends BaseMapper<HrPieceWageDetail> {
}
