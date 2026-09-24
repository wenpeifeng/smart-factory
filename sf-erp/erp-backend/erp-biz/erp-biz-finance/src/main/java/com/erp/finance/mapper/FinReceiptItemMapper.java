package com.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.finance.entity.FinReceiptItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收款核销明细 Mapper。
 */
@Mapper
public interface FinReceiptItemMapper extends BaseMapper<FinReceiptItem> {
}
