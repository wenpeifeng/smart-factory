package com.erp.purchase.service.impl;

import com.erp.common.exception.BusinessException;
import com.erp.purchase.dto.vo.SupplierStatementVO;
import com.erp.purchase.mapper.BasedataMapper;
import com.erp.purchase.mapper.PurInstockMapper;
import com.erp.purchase.mapper.PurReturnMapper;
import com.erp.purchase.service.PurStatementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * 供应商对账单 Service 实现（只读统计，无任何写操作）。
 * 口径：仅统计【已审核】入库单/退货单，按单据日期归属月份；
 * 净额 = 入库金额 - 退货金额，HALF_UP 2 位。
 */
@Service
@RequiredArgsConstructor
public class PurStatementServiceImpl implements PurStatementService {

    private final PurInstockMapper instockMapper;
    private final PurReturnMapper returnMapper;
    private final BasedataMapper basedataMapper;

    @Override
    public SupplierStatementVO statement(Long supplierId, String month) {
        if (basedataMapper.countSupplierById(supplierId) == 0) {
            throw BusinessException.ofKey("erp.pur.supplier-not-exists", supplierId);
        }
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (DateTimeParseException | NullPointerException e) {
            throw BusinessException.ofKey("erp.pur.month-format-invalid", month);
        }
        // 月份按 [月初, 次月月初) 半开区间统计，避免 DATE 类型与 23:59:59 边界问题
        LocalDate begin = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);

        BigDecimal instockAmount = instockMapper.sumAuditedAmount(supplierId, begin, end);
        BigDecimal returnAmount = returnMapper.sumAuditedAmount(supplierId, begin, end);

        SupplierStatementVO vo = new SupplierStatementVO();
        vo.setSupplierId(supplierId);
        vo.setSupplierName(basedataMapper.selectSupplierName(supplierId));
        vo.setMonth(month);
        vo.setInstockAmount(instockAmount);
        vo.setReturnAmount(returnAmount);
        vo.setNetAmount(instockAmount.subtract(returnAmount).setScale(2, RoundingMode.HALF_UP));
        vo.setInstockCount(instockMapper.countAudited(supplierId, begin, end));
        vo.setReturnCount(returnMapper.countAudited(supplierId, begin, end));
        return vo;
    }
}
