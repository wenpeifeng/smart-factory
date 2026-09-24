package com.erp.finance.mapper;

import com.erp.finance.dto.vo.AgingRawVO;
import com.erp.finance.dto.vo.SubjectAggregateRawVO;
import com.erp.finance.dto.vo.TrialBalanceRawVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 财务复杂查询 Mapper（SQL 见 resources/mapper/finance/FinQueryMapper.xml）。
 * <p>
 * 职责：
 * <ul>
 *   <li>客商名称查询：直接 SELECT base_customer / base_supplier（跨模块只读表，
 *       不 import 对方代码，遵守并行协作约定）。两表由 basedata 模块建表（V10x），
 *       尚未落库时由 Service 层降级（名称返回 null，前端展示客户/供应商 ID）；</li>
 *   <li>科目汇总 / 试算平衡：基于已过账（POSTED）凭证分录聚合。</li>
 * </ul>
 */
@Mapper
public interface FinQueryMapper {

    /**
     * 按主键集合查询客户名称（id -> name 映射的原始行）。
     *
     * @param ids 客户 ID 集合（非空）
     * @return 行数据：包含 id / name 两列
     */
    List<Map<String, Object>> selectCustomerRows(@Param("ids") List<Long> ids);

    /**
     * 按主键集合查询供应商名称。
     *
     * @param ids 供应商 ID 集合（非空）
     * @return 行数据：包含 id / name 两列
     */
    List<Map<String, Object>> selectSupplierRows(@Param("ids") List<Long> ids);

    /**
     * 单科目（或全部科目）在区间内的借贷发生额与期初发生额聚合。
     * <p>
     * 只统计已过账（POSTED）凭证；subjectId 为 null 时统计全部科目合计。
     *
     * @param bookId    账套 ID（多账套隔离：只统计该账套的凭证）
     * @param subjectId 科目 ID（可空 = 全部科目）
     * @param from      区间开始日（含）
     * @param to        区间结束日（含）
     * @return 聚合结果（各求和列可能为 null，Service 层按 0 处理）
     */
    SubjectAggregateRawVO selectSubjectAggregate(@Param("bookId") Long bookId,
                                                 @Param("subjectId") Long subjectId,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);

    /**
     * 试算平衡表：全科目 期初/本期 借贷发生额原始求和（按科目分组）。
     *
     * @param bookId 账套 ID（多账套隔离：科目与凭证均限定该账套）
     * @param from   期间开始日（含），期初 = 该日之前的累计
     * @param to     期间结束日（含）
     * @return 每个科目一行的原始聚合（含无发生额的科目，金额为 0）
     */
    List<TrialBalanceRawVO> selectTrialBalanceRows(@Param("bookId") Long bookId,
                                                   @Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);

    /**
     * 账龄分析：全部未结清应收单（amount − settled_amount &gt; 0）。
     * <p>
     * 账龄分桶在 Java 层完成（锚点 = COALESCE(biz_date, DATE(create_time))），
     * SQL 只负责取未结清行；只取当前账套、未逻辑删除的记录。
     *
     * @param bookId 账套 ID
     * @return 未结清应收原始行（partnerId = customer_id）
     */
    List<AgingRawVO> selectUnsettledReceivableRows(@Param("bookId") Long bookId);

    /**
     * 账龄分析：全部未结清应付单（对称）。
     *
     * @param bookId 账套 ID
     * @return 未结清应付原始行（partnerId = supplier_id）
     */
    List<AgingRawVO> selectUnsettledPayableRows(@Param("bookId") Long bookId);
}
