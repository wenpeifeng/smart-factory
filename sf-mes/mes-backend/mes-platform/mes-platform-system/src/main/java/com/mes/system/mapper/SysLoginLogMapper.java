package com.mes.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.system.entity.SysLoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 Mapper。只写入与分页查询，不提供修改/删除。
 */
@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {
}
