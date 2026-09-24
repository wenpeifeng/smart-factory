package com.erp.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.sales.entity.SalDeliveryNotice;
import org.apache.ibatis.annotations.Mapper;

/** 销售发货通知单 Mapper（MyBatis-Plus 通用 CRUD）。复杂 SQL 见 {@link SalesLinkMapper}。 */
@Mapper
public interface SalDeliveryNoticeMapper extends BaseMapper<SalDeliveryNotice> {
}
