package com.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.finance.entity.FinAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 资金账户 Mapper。
 * <p>
 * 余额变动一律走 {@link #changeBalance} 条件 UPDATE（数据库层原子累加），
 * 禁止应用层读-改-写，防并发错账。
 */
@Mapper
public interface FinAccountMapper extends BaseMapper<FinAccount> {

    /**
     * 原子调整账户余额（SQL 见 resources/mapper/finance/FinAccountMapper.xml）。
     * <p>
     * 余额不足（减为负）时影响行数为 0，由调用方判 0 抛业务异常回滚。
     *
     * @param bookId 所属账套 ID（多账套隔离条件）
     * @param id     账户 ID
     * @param delta  变动额：正数增加（收款）、负数减少（付款）
     * @param amount 校验用绝对值（当 delta 为负时要求 current_balance >= amount）
     * @return 影响行数：1 成功；0 账户不存在/停用/跨账套或余额不足
     */
    int changeBalance(@Param("bookId") Long bookId, @Param("id") Long id,
                      @Param("delta") BigDecimal delta, @Param("amount") BigDecimal amount);
}
