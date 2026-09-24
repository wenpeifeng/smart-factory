package com.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.finance.dto.form.ReceivableForm;
import com.erp.finance.dto.request.ReceivableQuery;
import com.erp.finance.dto.vo.ReceivableVO;
import com.erp.finance.context.BookContext;
import com.erp.finance.entity.FinReceivable;
import com.erp.finance.mapper.FinReceivableMapper;
import com.erp.finance.service.FinReceivableService;
import com.erp.finance.support.PartnerNameLookup;
import com.erp.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 应收单服务实现。
 * <p>
 * 客户名称通过 {@link PartnerNameLookup} 只读查询 base_customer（并行协作约定）。
 */
@Service
@RequiredArgsConstructor
public class FinReceivableServiceImpl implements FinReceivableService {

    private final FinReceivableMapper receivableMapper;
    private final PartnerNameLookup partnerNameLookup;

    @Override
    public PageResult<ReceivableVO> page(ReceivableQuery query, long pageNum, long pageSize) {
        var wrapper = Wrappers.lambdaQuery(FinReceivable.class)
                .eq(FinReceivable::getBookId, BookContext.getBookId())
                .eq(query.customerId() != null, FinReceivable::getCustomerId, query.customerId())
                .eq(notBlank(query.status()), FinReceivable::getStatus, query.status())
                .like(notBlank(query.keyword()), FinReceivable::getBizNo, trim(query.keyword()))
                // 仅看未核销完结的单（含部分核销），SQL 直列条件（amount 与 settled_amount 同行比较）
                .apply(Boolean.TRUE.equals(query.unsettledOnly()), "amount > settled_amount");
        applyMonthRange(wrapper, query.month());
        wrapper.orderByDesc(FinReceivable::getId);

        PageResult<FinReceivable> page = PageUtils.of(receivableMapper, wrapper, pageNum, pageSize);
        Map<Long, String> names = partnerNameLookup.customerNames(
                page.getRecords().stream().map(FinReceivable::getCustomerId).toList());
        return page.map(r -> ReceivableVO.from(r, names.get(r.getCustomerId())));
    }

    @Override
    public ReceivableVO detail(Long id) {
        FinReceivable receivable = receivableMapper.selectById(id);
        if (receivable == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.receivable-not-exists", id);
        }
        BookContext.checkBookOwnership(receivable.getBookId(), "应收单[%s]".formatted(receivable.getBizNo()));
        Map<Long, String> names = partnerNameLookup.customerNames(List.of(receivable.getCustomerId()));
        return ReceivableVO.from(receivable, names.get(receivable.getCustomerId()));
    }

    @Override
    public Long create(ReceivableForm form) {
        FinReceivable receivable = new FinReceivable();
        receivable.setBookId(BookContext.getBookId());
        receivable.setCustomerId(form.customerId());
        receivable.setBizNo(form.bizNo());
        receivable.setBizDate(form.bizDate());
        receivable.setDueDate(form.dueDate());
        receivable.setAmount(form.amount());
        receivable.setSettledAmount(java.math.BigDecimal.ZERO);
        receivable.setStatus("UNSETTLED");
        receivable.setRemark(form.remark());
        receivableMapper.insert(receivable);
        return receivable.getId();
    }

    /** 业务月份（yyyy-MM）转 biz_date 区间条件；格式非法时静默忽略 */
    private void applyMonthRange(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FinReceivable> wrapper,
                                 String month) {
        if (notBlank(month)) {
            try {
                YearMonth ym = YearMonth.parse(trim(month));
                wrapper.ge(FinReceivable::getBizDate, ym.atDay(1));
                wrapper.le(FinReceivable::getBizDate, ym.atEndOfMonth());
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
