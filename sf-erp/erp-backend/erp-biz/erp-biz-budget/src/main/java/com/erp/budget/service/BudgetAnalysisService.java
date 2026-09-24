package com.erp.budget.service;

import com.erp.budget.dto.vo.BudgetAnalysisRowVO;
import com.erp.budget.dto.vo.BudgetMonthlyCurveVO;

import java.util.List;

/**
 * 预算执行分析 Service —— 部门×科目 的预算/已用/余额/执行率 与月度执行曲线。
 * <p>
 * 已用口径与 {@link BudgetControlService} 一致：凭证口径（只读约定表）+ 登记口径。
 */
public interface BudgetAnalysisService {

    /**
     * 部门×科目执行分析（全年口径）。
     *
     * @param planId 方案 ID（必填）
     * @param deptId 部门筛选（空 = 全部门）
     * @return 分析行（预算/已用/余额/执行率%，按部门与科目编码排序）
     * @throws com.erp.common.exception.BusinessException 方案不存在
     */
    List<BudgetAnalysisRowVO> analysis(Long planId, Long deptId);

    /**
     * 月度执行曲线数据（1-12 月逐月 预算/已用/执行率）。
     *
     * @param planId    方案 ID（必填）
     * @param deptId    部门筛选（空 = 全部门）
     * @param subjectId 科目筛选（空 = 全科目）
     * @throws com.erp.common.exception.BusinessException 方案不存在
     */
    List<BudgetMonthlyCurveVO> monthlyCurve(Long planId, Long deptId, Long subjectId);
}
