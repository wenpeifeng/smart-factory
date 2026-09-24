package com.erp.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.sales.entity.SalOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 销售订单 Mapper（MyBatis-Plus 通用 CRUD）。
 * 复杂查询（关联客户/仓库/物料）与跨模块 SQL 见 {@link SalesBizMapper}。
 */
@Mapper
public interface SalOrderMapper extends BaseMapper<SalOrder> {
}
