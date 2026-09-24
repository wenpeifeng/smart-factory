package com.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.entity.SysOperLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper。只写入与分页查询，不提供修改/删除。
 */
@Mapper
public interface SysOperLogMapper extends BaseMapper<SysOperLog> {
}
