package com.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.finance.entity.FinReceivable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 应收单 Mapper。
 * <p>
 * 核销回写走 {@link #applyVerify} 条件 UPDATE：
 * 单条 SQL 同时累加 settled_amount 并联动状态（全额→SETTLED / 部分→PARTIAL），
 * WHERE 条件兜底"核销额不得超过未核销余额"，并发下也不会超核销。
 */
@Mapper
public interface FinReceivableMapper extends BaseMapper<FinReceivable> {

    /**
     * 原子核销一笔应收（SQL 见 resources/mapper/finance/FinReceivableMapper.xml）。
     *
     * @param bookId 所属账套 ID（多账套隔离条件）
     * @param id     应收单 ID
     * @param amount 本次核销额（必须 &gt; 0）
     * @return 影响行数：1 成功；0 单据不存在/跨账套或核销额超过未核销余额
     */
    int applyVerify(@Param("bookId") Long bookId, @Param("id") Long id, @Param("amount") BigDecimal amount);
}
