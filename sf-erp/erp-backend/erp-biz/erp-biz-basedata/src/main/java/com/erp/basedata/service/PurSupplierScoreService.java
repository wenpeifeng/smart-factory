package com.erp.basedata.service;

import com.erp.basedata.dto.vo.SupplierGradeSummaryVO;
import com.erp.basedata.dto.vo.SupplierScoreVO;
import com.erp.common.result.PageResult;

/**
 * 供应商交易评分 Service（V141）。
 * <p>
 * 定时器（月度）+ 手工重算：按月/季从 pur_order / pur_instock / qc_inspect /
 * pur_return 聚合各供应商交易表现，加权评分并定级 A/B/C/D。
 */
public interface PurSupplierScoreService {

    /**
     * 计算指定周期的全部供应商评分（重算=先删后插，幂等）。
     *
     * @param periodType MONTH / QUARTER
     * @param period     周期标识（yyyy-MM / yyyy-Qn；空=最近一个已完结周期）
     * @return 参与评分的供应商数
     */
    int recalc(String periodType, String period);

    /**
     * 评分分页查询。
     *
     * @param pageNum     页码
     * @param pageSize    页大小
     * @param supplierId  供应商（可空）
     * @param periodType  周期类型（可空）
     * @param period      周期标识（可空）
     * @param grade       等级 A/B/C/D（可空）
     * @return 分页结果（含供应商编码/名称冗余）
     */
    PageResult<SupplierScoreVO> page(long pageNum, long pageSize, Long supplierId,
                                     String periodType, String period, String grade);

    /**
     * 汇总报表：指定周期等级分布 + 均值 + 交易额合计。
     *
     * @param periodType MONTH / QUARTER（空=MONTH）
     * @param period     周期标识（空=最近一个有数据的周期）
     */
    SupplierGradeSummaryVO summary(String periodType, String period);
}
