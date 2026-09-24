package com.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.finance.entity.FinAccountFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 账户流水 Mapper。
 * <p>
 * 收入/支出合计统计走 {@link #sumAmountByDirection}（SQL 见 XML），
 * 用于账户余额快照接口（期初 + 收入 - 支出 与当前余额对账）。
 */
@Mapper
public interface FinAccountFlowMapper extends BaseMapper<FinAccountFlow> {

    /**
     * 按方向统计账户流水金额合计。
     *
     * @param bookId    所属账套 ID（多账套隔离条件）
     * @param accountId 账户 ID
     * @param direction 收支方向：INCOME-收入 EXPENSE-支出
     * @return 金额合计；无流水返回 null（由调用方按 0 处理）
     */
    BigDecimal sumAmountByDirection(@Param("bookId") Long bookId,
                                    @Param("accountId") Long accountId,
                                    @Param("direction") String direction);
}
