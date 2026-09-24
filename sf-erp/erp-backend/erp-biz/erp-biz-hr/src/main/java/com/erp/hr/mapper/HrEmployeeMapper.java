package com.erp.hr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.hr.entity.HrEmployee;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工档案 Mapper（MP 通用 CRUD）。
 */
@Mapper
public interface HrEmployeeMapper extends BaseMapper<HrEmployee> {
}
