package com.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.finance.context.BookContext;
import com.erp.finance.dto.vo.PeriodStatRowVO;
import com.erp.finance.dto.vo.PeriodVO;
import com.erp.finance.entity.FinPeriod;
import com.erp.finance.mapper.FinPeriodMapper;
import com.erp.finance.service.FinPeriodService;
import com.erp.framework.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 总账会计期间服务实现（V521）。
 * <p>
 * 期间判定 = 凭证日期所在自然月（yyyyMM）；与 erp-cost 的 cost_period
 * （成本计算期间）相互独立。结账前校验该期凭证全部 POSTED；
 * CLOSED 期间对凭证增删改过账的拦截见 {@link #assertPeriodOpen}，
 * 由 FinVoucherServiceImpl 四个入口调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinPeriodServiceImpl implements FinPeriodService {

    /** 期间格式：yyyyMM */
    private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{6}$");

    /** 期间格式化（凭证日期 → yyyyMM） */
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final FinPeriodMapper periodMapper;

    @Override
    public List<PeriodVO> list() {
        Long bookId = BookContext.getBookId();
        // 月份聚合（当前账套全部凭证，含 DRAFT/POSTED）
        Map<String, PeriodStatRowVO> stats = periodMapper.selectPeriodStats(bookId).stream()
                .collect(Collectors.toMap(PeriodStatRowVO::period, r -> r));
        List<PeriodVO> rows = new ArrayList<>();
        // 出现过凭证的月份
        stats.forEach((period, stat) -> rows.add(new PeriodVO(period,
                FinPeriod.STATUS_OPEN, stat.voucherCount(), stat.draftCount(), stat.postedCount(),
                null, null, null, null)));
        // fin_period 已建行（覆盖状态/结账人；未出现在凭证聚合里的空期间也列出）
        for (FinPeriod p : periodMapper.selectList(Wrappers.lambdaQuery(FinPeriod.class)
                .eq(FinPeriod::getBookId, bookId))) {
            PeriodStatRowVO stat = stats.get(p.getPeriod());
            rows.removeIf(r -> r.period().equals(p.getPeriod()));
            rows.add(new PeriodVO(p.getPeriod(), p.getStatus(),
                    stat == null ? 0L : stat.voucherCount(),
                    stat == null ? 0L : stat.draftCount(),
                    stat == null ? 0L : stat.postedCount(),
                    p.getCloseBy(), p.getCloseTime(), p.getReopenBy(), p.getReopenTime()));
        }
        rows.sort(Comparator.comparing(PeriodVO::period).reversed());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(String period) {
        validatePeriodFormat(period);
        Long bookId = BookContext.getBookId();
        Long draftCount = periodMapper.countDraftVouchers(bookId, period);
        if (draftCount != null && draftCount > 0) {
            throw BusinessException.ofKey("erp.fin.period-has-draft-vouchers", period, draftCount);
        }
        FinPeriod row = findByBookAndPeriod(bookId, period);
        if (row == null) {
            row = new FinPeriod();
            row.setBookId(bookId);
            row.setPeriod(period);
            row.setStatus(FinPeriod.STATUS_CLOSED);
            row.setCloseBy(SecurityUtils.getCurrentUserId());
            row.setCloseTime(LocalDateTime.now());
            periodMapper.insert(row);
        } else {
            if (FinPeriod.STATUS_CLOSED.equals(row.getStatus())) {
                throw BusinessException.ofKey("erp.fin.period-closed-no-repeat", period);
            }
            row.setStatus(FinPeriod.STATUS_CLOSED);
            row.setCloseBy(SecurityUtils.getCurrentUserId());
            row.setCloseTime(LocalDateTime.now());
            periodMapper.updateById(row);
        }
        log.info("[期间][结账] 账套={} 期间={} 操作人={}", bookId, period, SecurityUtils.getCurrentUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reopen(String period) {
        validatePeriodFormat(period);
        Long bookId = BookContext.getBookId();
        FinPeriod row = findByBookAndPeriod(bookId, period);
        if (row == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.period-open-no-unclose", period);
        }
        if (!FinPeriod.STATUS_CLOSED.equals(row.getStatus())) {
            throw BusinessException.ofKey("erp.fin.period-open-no-unclose2", period);
        }
        row.setStatus(FinPeriod.STATUS_OPEN);
        row.setReopenBy(SecurityUtils.getCurrentUserId());
        row.setReopenTime(LocalDateTime.now());
        periodMapper.updateById(row);
        log.info("[期间][反结账] 账套={} 期间={} 操作人={}", bookId, period, SecurityUtils.getCurrentUserId());
    }

    @Override
    public void assertPeriodOpen(LocalDate voucherDate, Long bookId, String action) {
        if (voucherDate == null) {
            return;
        }
        String period = voucherDate.format(PERIOD_FORMAT);
        FinPeriod row = findByBookAndPeriod(bookId, period);
        if (row != null && FinPeriod.STATUS_CLOSED.equals(row.getStatus())) {
            throw BusinessException.ofKey("erp.fin.period-closed-op-forbidden", period, action);
        }
    }

    private FinPeriod findByBookAndPeriod(Long bookId, String period) {
        return periodMapper.selectOne(Wrappers.lambdaQuery(FinPeriod.class)
                .eq(FinPeriod::getBookId, bookId)
                .eq(FinPeriod::getPeriod, period)
                .last("LIMIT 1"));
    }

    private static void validatePeriodFormat(String period) {
        if (period == null || !PERIOD_PATTERN.matcher(period).matches()) {
            throw BusinessException.ofKey("erp.fin.period-format-invalid", period);
        }
        try {
            LocalDate.parse(period.substring(0, 4) + "-" + period.substring(4) + "-01");
        } catch (DateTimeParseException e) {
            throw BusinessException.ofKey("erp.fin.period-invalid", period);
        }
    }
}
