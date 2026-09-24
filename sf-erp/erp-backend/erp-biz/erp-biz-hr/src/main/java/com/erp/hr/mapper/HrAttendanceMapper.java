package com.erp.hr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.hr.entity.HrAttendance;
import org.apache.ibatis.annotations.Mapper;

/**
 * 考勤记录 Mapper（MP 通用 CRUD；导入覆盖走 HrStatMapper.upsertAttendance）。
 */
@Mapper
public interface HrAttendanceMapper extends BaseMapper<HrAttendance> {
}
