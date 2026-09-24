package com.erp.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.notify.entity.NotifySendLog;
import org.apache.ibatis.annotations.Mapper;

/** 通知发送日志 Mapper（只增不改） */
@Mapper
public interface NotifySendLogMapper extends BaseMapper<NotifySendLog> {
}
