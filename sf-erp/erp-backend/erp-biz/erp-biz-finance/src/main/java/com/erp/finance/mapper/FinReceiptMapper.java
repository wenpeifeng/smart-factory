package com.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.finance.entity.FinReceipt;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收款单主表 Mapper。
 */
@Mapper
public interface FinReceiptMapper extends BaseMapper<FinReceipt> {
}
