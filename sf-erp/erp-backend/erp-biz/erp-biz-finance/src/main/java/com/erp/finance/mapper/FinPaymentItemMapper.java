package com.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.finance.entity.FinPaymentItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 付款核销明细 Mapper。
 */
@Mapper
public interface FinPaymentItemMapper extends BaseMapper<FinPaymentItem> {
}
