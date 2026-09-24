package com.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.finance.entity.FinPayable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 应付单 Mapper。
 * <p>
 * 付款核销回写走 {@link #applyVerify} 条件 UPDATE，与应收核销完全对称。
 */
@Mapper
public interface FinPayableMapper extends BaseMapper<FinPayable> {

    /**
     * 原子核销一笔应付（SQL 见 resources/mapper/finance/FinPayableMapper.xml）。
     *
     * @param bookId 所属账套 ID（多账套隔离条件）
     * @param id     应付单 ID
     * @param amount 本次核销额（必须 &gt; 0）
     * @return 影响行数：1 成功；0 单据不存在/跨账套或核销额超过未核销余额
     */
    int applyVerify(@Param("bookId") Long bookId, @Param("id") Long id, @Param("amount") BigDecimal amount);
}
