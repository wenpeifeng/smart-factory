package com.erp.cost.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.cost.dto.raw.MonthlyTotalRaw;
import com.erp.cost.dto.raw.SummaryRaw;
import com.erp.cost.dto.request.CostFlowQuery;
import com.erp.cost.dto.vo.CostFlowVO;
import com.erp.cost.entity.CostFlow;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 存货计价流水 Mapper。
 * <p>
 * 计价核心原语 {@link #selectLastForUpdate} 以行锁串行化同一物料的计价过账；
 * 重算原语 {@link #deleteFromPeriod} 物理删除（cost_flow 是可重建的运行台账，
 * 重算=删后重放，逻辑删除会占用索引且无法表达「重建」语义）。
 * 报表原语（分页/收发存汇总/月度趋势）见 XML。
 */
public interface CostFlowMapper extends BaseMapper<CostFlow> {

    /**
     * 取物料最新一行计价流水并加行锁（FOR UPDATE）。
     * <p>
     * 锁的是 (material_id, id) 索引上的匹配范围，同一物料的并发过账在此排队，
     * 保证「读运行余额 → 计算新余额 → 写入」的原子性；无行返回 null（无成本历史）。
     */
    CostFlow selectLastForUpdate(@Param("materialId") Long materialId);

    /** 物理删除 [fromPeriod, toPeriod] 闭区间的计价流水（重算用；调用方负责拦截已结账期间） */
    int deleteByPeriodRange(@Param("fromPeriod") String fromPeriod, @Param("toPeriod") String toPeriod);

    /** 计价流水分页（带物料/仓库名称回显，筛选：期间/物料/业务类型/单号关键词） */
    IPage<CostFlowVO> selectFlowPage(IPage<CostFlowVO> page, @Param("q") CostFlowQuery query);

    /** 收发存汇总（按物料）：期初取 period 前最后一行运行余额，本期入/出按符号汇总 */
    List<SummaryRaw> selectPeriodSummary(@Param("period") String period);

    /** 月度合计（趋势图）：入库/出库金额 + 销售出库/生产领料成本拆分 */
    List<MonthlyTotalRaw> selectMonthlyTotals(@Param("fromPeriod") String fromPeriod,
                                              @Param("toPeriod") String toPeriod);

    /** fromPeriod 之前的期初结存总额（每个物料取 period < fromPeriod 的最后一行 balance_cost 求和） */
    BigDecimal selectOpeningBalance(@Param("fromPeriod") String fromPeriod);
}
