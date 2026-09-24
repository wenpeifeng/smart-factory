package com.erp.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.invoice.entity.InvSalesInvoice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销项发票 Mapper：通用 CRUD 走 MP BaseMapper；
 * 开票 / 红冲的条件状态流转 SQL 见 resources/mapper/invoice/InvSalesInvoiceMapper.xml。
 */
@Mapper
public interface InvSalesInvoiceMapper extends BaseMapper<InvSalesInvoice> {

    /**
     * 开票：状态 0(待开票) → 10(已开票)，同时写入开票日期与开票人。
     *
     * @param id      发票 ID
     * @param date    开票日期（当天）
     * @param issueBy 开票人用户 ID
     * @param now     开票时间
     * @return 影响行数（0 = 单据不存在 / 状态已变更，调用方按并发冲突处理）
     */
    int issueInvoice(@Param("id") Long id, @Param("date") LocalDate date,
                     @Param("issueBy") Long issueBy, @Param("now") LocalDateTime now);

    /**
     * 红冲原票：状态 10(已开票) → 20(已红冲)。
     *
     * @param id 发票 ID
     * @return 影响行数（0 = 状态已变更）
     */
    int reverseInvoice(@Param("id") Long id);
}
