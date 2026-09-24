package com.erp.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.basedata.entity.BaseCustomer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户 Mapper。
 */
@Mapper
public interface BaseCustomerMapper extends BaseMapper<BaseCustomer> {
}
