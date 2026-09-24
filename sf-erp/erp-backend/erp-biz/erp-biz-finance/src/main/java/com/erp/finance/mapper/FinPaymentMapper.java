package com.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.finance.entity.FinPayment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 付款单主表 Mapper。
 */
@Mapper
public interface FinPaymentMapper extends BaseMapper<FinPayment> {
}
