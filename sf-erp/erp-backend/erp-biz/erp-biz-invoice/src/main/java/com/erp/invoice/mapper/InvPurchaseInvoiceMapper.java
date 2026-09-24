package com.erp.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.invoice.entity.InvPurchaseInvoice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 进项发票 Mapper：通用 CRUD 走 MP BaseMapper；
 * 认证 / 红冲的条件状态流转 SQL 见 resources/mapper/invoice/InvPurchaseInvoiceMapper.xml。
 */
@Mapper
public interface InvPurchaseInvoiceMapper extends BaseMapper<InvPurchaseInvoice> {

    /**
     * 认证：状态 0(待认证) → 10(已认证)，写认证日期与认证人。
     *
     * @param id      发票 ID
     * @param date    认证日期（当天）
     * @param authBy  认证人用户 ID
     * @return 影响行数（0 = 单据不存在 / 状态已变更）
     */
    int authenticateInvoice(@Param("id") Long id, @Param("date") LocalDate date, @Param("authBy") Long authBy);

    /**
     * 红冲原票：状态 10(已认证) → 20(已红冲)。
     *
     * @param id 发票 ID
     * @return 影响行数（0 = 状态已变更）
     */
    int reverseInvoice(@Param("id") Long id);
}
