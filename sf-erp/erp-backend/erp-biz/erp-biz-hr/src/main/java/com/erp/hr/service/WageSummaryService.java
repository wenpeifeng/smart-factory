package com.erp.hr.service;

import com.erp.hr.dto.vo.WageSummaryVO;

import java.util.List;

/**
 * 工资汇总服务 —— 员工应发 = 计件合计 + 加班天数×50 − 月薪基准/30×缺勤天数。
 * <p>
 * 仅计算汇总，不生成凭证（与 finance 的凭证联动由后续扩展实现）。
 */
public interface WageSummaryService {

    /**
     * 月度工资汇总。
     *
     * @param month 月份（yyyy-MM）
     * @return 员工维度汇总行（在职 ∪ 当月有考勤/计件数据）
     */
    List<WageSummaryVO> summary(String month);
}
