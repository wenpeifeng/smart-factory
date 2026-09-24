package com.mes.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.integration.erp.push.ErpPushLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * ERP 回传推送日志 Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface ErpPushLogMapper extends BaseMapper<ErpPushLog> {
}
