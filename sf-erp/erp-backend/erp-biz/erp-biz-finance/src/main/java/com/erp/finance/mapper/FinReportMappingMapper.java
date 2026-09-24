package com.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.finance.entity.FinReportMapping;
import org.apache.ibatis.annotations.Mapper;

/**
 * 财务报表取数映射 Mapper（V531 fin_report_mapping）。
 * <p>
 * 报表引擎读取行项目配置（report_type + sort_no 排序），无业务写入。
 */
@Mapper
public interface FinReportMappingMapper extends BaseMapper<FinReportMapping> {
}
