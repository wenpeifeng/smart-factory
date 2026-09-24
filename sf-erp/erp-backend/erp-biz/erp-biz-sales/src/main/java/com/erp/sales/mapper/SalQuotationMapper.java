package com.erp.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.sales.entity.SalQuotation;
import org.apache.ibatis.annotations.Mapper;

/** 销售报价单 Mapper（MyBatis-Plus 通用 CRUD）。复杂 SQL 见 {@link SalesLinkMapper}。 */
@Mapper
public interface SalQuotationMapper extends BaseMapper<SalQuotation> {
}
