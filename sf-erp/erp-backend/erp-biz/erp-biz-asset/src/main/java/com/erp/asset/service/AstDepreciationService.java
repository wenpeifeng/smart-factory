package com.erp.asset.service;

import com.erp.asset.dto.vo.DepreciationPeriodDetailVO;
import com.erp.asset.dto.vo.DepreciationPeriodVO;
import com.erp.asset.dto.vo.DepreciationRunVO;
import com.erp.asset.dto.vo.StatisticsVO;
import com.erp.common.result.PageResult;

/**
 * 折旧计提服务：期间管理、月度计提执行、部门/类别统计。
 */
public interface AstDepreciationService {

    /**
     * 折旧期间分页（按期间倒序）。
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @return 期间 VO 分页
     */
    PageResult<DepreciationPeriodVO> page(long pageNum, long pageSize);

    /**
     * 期间详情：期间头 + 逐资产明细 + 合计。
     *
     * @param period 会计期间（yyyyMM）
     * @return 期间详情
     * @throws com.erp.common.exception.BusinessException 期间格式非法 / 期间尚未计提
     */
    DepreciationPeriodDetailVO detail(String period);

    /**
     * 执行某期间折旧计提（同一期间只能成功执行一次）：
     * 全部"在用且已提月数 &lt; 预计年限"的资产 → 每资产月折旧额
     * （(原值−残值)/预计年限月，HALF_UP 2 位）→ 一张汇总凭证
     * （借 折旧费用按部门汇总多行 / 贷 累计折旧，POSTED）→
     * 资产累计折旧与已提月数条件 UPDATE +1 → 期间 CLOSED。
     *
     * @param period 会计期间（yyyyMM）
     * @return 执行结果（资产数/总额/凭证）
     * @throws com.erp.common.exception.BusinessException 期间格式非法 / 期间已计提关闭 / 并发计提冲突
     */
    DepreciationRunVO run(String period);

    /**
     * 资产统计：截至某期间，部门/类别维度的原值、累计折旧、净值汇总
     * （口径：未删除 + 未处置 + 入账日期 ≤ 期间末；累计折旧按明细期间 ≤ 给定期间汇总）。
     *
     * @param period 会计期间（yyyyMM，可空 = 当月）
     * @return 统计结果（含双维度行与合计）
     * @throws com.erp.common.exception.BusinessException 期间格式非法
     */
    StatisticsVO statistics(String period);
}
