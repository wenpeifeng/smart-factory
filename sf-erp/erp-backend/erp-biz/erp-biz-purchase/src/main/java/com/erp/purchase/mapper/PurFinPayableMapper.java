package com.erp.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.purchase.entity.FinPayable;

/**
 * 应付单 Mapper——【跨模块约定表 fin_payable，归 finance 模块所有】。
 * 采购模块仅在入库审核事务内 INSERT 一条未结算应付，不做 UPDATE/DELETE。
 */
public interface PurFinPayableMapper extends BaseMapper<FinPayable> {
}
