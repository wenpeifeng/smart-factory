package com.erp.system.service;

import com.erp.common.result.PageResult;
import com.erp.system.dto.log.OperLogQuery;
import com.erp.system.dto.log.OperLogVO;

/**
 * 操作日志查询服务（写入由 AOP 切面直接落库）。
 */
public interface SysOperLogService {

    /**
     * 分页查询操作日志。
     *
     * @param query 查询条件（模块/操作人/结果/时间段）
     * @return 日志 VO 分页
     */
    PageResult<OperLogVO> page(OperLogQuery query);
}
