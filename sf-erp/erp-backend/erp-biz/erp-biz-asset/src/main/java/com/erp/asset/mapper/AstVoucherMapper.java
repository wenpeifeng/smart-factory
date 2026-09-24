package com.erp.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.asset.entity.FinVoucher;
import org.apache.ibatis.annotations.Mapper;

/**
 * 凭证主表写入 Mapper（fin_voucher，本模块按协作约定直写，类名加 Ast 前缀避免与
 * erp-finance 同名 Mapper Bean 冲突）。
 */
@Mapper
public interface AstVoucherMapper extends BaseMapper<FinVoucher> {
}
