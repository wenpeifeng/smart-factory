package com.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.finance.entity.FinBook;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账套档案 Mapper（标准 MyBatis-Plus BaseMapper，无手写 SQL）。
 */
@Mapper
public interface FinBookMapper extends BaseMapper<FinBook> {
}
