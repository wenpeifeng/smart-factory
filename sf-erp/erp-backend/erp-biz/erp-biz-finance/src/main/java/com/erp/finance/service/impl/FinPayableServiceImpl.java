package com.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.finance.dto.form.PayableForm;
import com.erp.finance.dto.request.PayableQuery;
import com.erp.finance.dto.vo.PayableVO;
import com.erp.finance.context.BookContext;
import com.erp.finance.entity.FinPayable;
import com.erp.finance.mapper.FinPayableMapper;
import com.erp.finance.service.FinPayableService;
import com.erp.finance.support.PartnerNameLookup;
import com.erp.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 应付单服务实现（与应收单完全对称，供应商名称只读查询 base_supplier）。
 */
@Service
@RequiredArgsConstructor
public class FinPayableServiceImpl implements FinPayableService {

    private final FinPayableMapper payableMapper;
    private final PartnerNameLookup partnerNameLookup;

    @Override
    public PageResult<PayableVO> page(PayableQuery query, long pageNum, long pageSize) {
        var wrapper = Wrappers.lambdaQuery(FinPayable.class)
                .eq(FinPayable::getBookId, BookContext.getBookId())
                .eq(query.supplierId() != null, FinPayable::getSupplierId, query.supplierId())
                .eq(notBlank(query.status()), FinPayable::getStatus, query.status())
                .like(notBlank(query.keyword()), FinPayable::getBizNo, trim(query.keyword()))
                .apply(Boolean.TRUE.equals(query.unsettledOnly()), "amount > settled_amount");
        applyMonthRange(wrapper, query.month());
        wrapper.orderByDesc(FinPayable::getId);

        PageResult<FinPayable> page = PageUtils.of(payableMapper, wrapper, pageNum, pageSize);
        Map<Long, String> names = partnerNameLookup.supplierNames(
                page.getRecords().stream().map(FinPayable::getSupplierId).toList());
        return page.map(p -> PayableVO.from(p, names.get(p.getSupplierId())));
    }

    @Override
    public PayableVO detail(Long id) {
        FinPayable payable = payableMapper.selectById(id);
        if (payable == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.payable-not-exists", id);
        }
        BookContext.checkBookOwnership(payable.getBookId(), "应付单[%s]".formatted(payable.getBizNo()));
        Map<Long, String> names = partnerNameLookup.supplierNames(List.of(payable.getSupplierId()));
        return PayableVO.from(payable, names.get(payable.getSupplierId()));
    }

    @Override
    public Long create(PayableForm form) {
        FinPayable payable = new FinPayable();
        payable.setBookId(BookContext.getBookId());
        payable.setSupplierId(form.supplierId());
        payable.setBizNo(form.bizNo());
        payable.setBizDate(form.bizDate());
        payable.setDueDate(form.dueDate());
        payable.setAmount(form.amount());
        payable.setSettledAmount(BigDecimal.ZERO);
        payable.setStatus("UNSETTLED");
        payable.setRemark(form.remark());
        payableMapper.insert(payable);
        return payable.getId();
    }

    /** 业务月份（yyyy-MM）转 biz_date 区间条件；格式非法时静默忽略 */
    private void applyMonthRange(LambdaQueryWrapper<FinPayable> wrapper, String month) {
        if (notBlank(month)) {
            try {
                YearMonth ym = YearMonth.parse(trim(month));
                wrapper.ge(FinPayable::getBizDate, ym.atDay(1));
                wrapper.le(FinPayable::getBizDate, ym.atEndOfMonth());
            } catch (DateTimeParseException ignored) {
                // 非法月份格式不参与过滤
            }
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
