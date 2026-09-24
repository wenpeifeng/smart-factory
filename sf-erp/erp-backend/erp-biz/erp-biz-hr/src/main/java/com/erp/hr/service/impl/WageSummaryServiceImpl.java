package com.erp.hr.service.impl;

import com.erp.common.exception.BusinessException;
import com.erp.hr.constant.HrConstants;
import com.erp.hr.dto.vo.WageSummaryVO;
import com.erp.hr.mapper.HrStatMapper;
import com.erp.hr.service.WageSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 工资汇总服务实现 —— 底稿（考勤聚合+计件合计+月薪基准）由 HrStatMapper 一次查出，
 * 加班费/缺勤扣款/应发在 Java 侧 DECIMAL 计算（HALF_UP 2 位）。
 */
@Service
@RequiredArgsConstructor
public class WageSummaryServiceImpl implements WageSummaryService {

    private final HrStatMapper statMapper;

    @Override
    public List<WageSummaryVO> summary(String month) {
        if (month == null || !month.matches("\\d{4}-\\d{2}")) {
            throw BusinessException.ofKey("erp.hr.month-format-invalid", month);
        }
        List<WageSummaryVO> rows = statMapper.wageSummaryRows(month);
        for (WageSummaryVO row : rows) {
            BigDecimal overtimeDays = BigDecimal.valueOf(row.getOvertimeDays());
            BigDecimal absentDays = BigDecimal.valueOf(row.getAbsentDays());
            BigDecimal baseSalary = row.getBaseSalary() == null ? BigDecimal.ZERO : row.getBaseSalary();

            // 加班费 = 加班天数 × 50（常量，见 HrConstants）
            row.setOvertimePay(overtimeDays.multiply(HrConstants.OVERTIME_ALLOWANCE_PER_DAY)
                    .setScale(2, RoundingMode.HALF_UP));
            // 缺勤扣款 = 月薪基准 / 30 × 缺勤天数
            row.setAbsentDeduct(baseSalary.divide(HrConstants.ABSENT_DEDUCT_BASE_DAYS, 6, RoundingMode.HALF_UP)
                    .multiply(absentDays)
                    .setScale(2, RoundingMode.HALF_UP));
            // 应发 = 计件 + 加班费 − 缺勤扣款
            row.setTotalAmount(row.getPieceAmount()
                    .add(row.getOvertimePay())
                    .subtract(row.getAbsentDeduct())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        return rows;
    }
}
