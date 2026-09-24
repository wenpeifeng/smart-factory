package com.erp.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.asset.entity.FinVoucherEntry;
import org.apache.ibatis.annotations.Mapper;

/**
 * 凭证分录写入 Mapper（fin_voucher_entry，本模块按协作约定直写）。
 */
@Mapper
public interface AstVoucherEntryMapper extends BaseMapper<FinVoucherEntry> {
}
