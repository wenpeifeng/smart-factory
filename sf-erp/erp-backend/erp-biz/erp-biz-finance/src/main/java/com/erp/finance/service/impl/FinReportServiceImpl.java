package com.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.finance.context.BookContext;
import com.erp.finance.dto.vo.AgingRawVO;
import com.erp.finance.dto.vo.BalanceSheetVO;
import com.erp.finance.dto.vo.IncomeStatementVO;
import com.erp.finance.dto.vo.PartnerAgingVO;
import com.erp.finance.dto.vo.TrialBalanceRawVO;
import com.erp.finance.entity.FinBook;
import com.erp.finance.entity.FinReportMapping;
import com.erp.finance.mapper.FinBookMapper;
import com.erp.finance.mapper.FinQueryMapper;
import com.erp.finance.mapper.FinReportMappingMapper;
import com.erp.finance.service.FinReportService;
import com.erp.finance.support.PartnerNameLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 财务三大报表服务实现（映射驱动报表引擎）。
 *
 * <h3>取数原理</h3>
 * 科目余额唯一来源 = 已过账（POSTED）凭证分录（本系统无独立余额表，无凭证外期初），
 * 复用试算平衡的聚合 SQL（{@code selectTrialBalanceRows}）：一次查询同时得到
 * 每科目「期初借/贷 + 本期借/贷」，任意方向的净额均可推导：
 * <ul>
 *   <li>借方净额 = (期初借+本期借) − (期初贷+本期贷)，资产侧 / 成本费用行；</li>
 *   <li>贷方净额 = (期初贷+本期贷) − (期初借+本期借)，负债权益侧 / 收入行；</li>
 *   <li>减项科目（累计折旧/坏账准备，贷方余额）按借方净额取数自然为负数，直接冲减资产合计。</li>
 * </ul>
 *
 * <h3>映射驱动</h3>
 * 行项目 ← fin_report_mapping 配置（subject_codes 前缀匹配，2221 命中 222101），
 * 计算行（资产总计/负债合计/…/净利润）= 上方数据行的运行合计；
 * 未分配利润行 include_pl=1 时并入损益类科目未结转净额，
 * 使全部科目被报表覆盖 ⇒ 资产总计 = 负债 + 所有者权益（勾稽 tolerance 0.01）。
 * 期末存在未被映射覆盖且余额非 0 的科目时输出 warning（不平的排查线索）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinReportServiceImpl implements FinReportService {

    /** 报表类型：资产负债表 */
    private static final String TYPE_BS = "BALANCE_SHEET";
    /** 报表类型：利润表 */
    private static final String TYPE_IS = "INCOME_STATEMENT";
    /** 科目类别：损益 */
    private static final String CATEGORY_PL = "PROFIT_LOSS";
    /** 勾稽平衡容差（分位级） */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    /** 非零判定（半分位级） */
    private static final BigDecimal EPS = new BigDecimal("0.005");

    private static final String SIDE_ASSET = "ASSET";
    private static final String SIDE_LIABILITY = "LIABILITY";
    private static final String SIDE_EQUITY = "EQUITY";
    private static final String DIR_DEBIT = "DEBIT";

    private final FinQueryMapper queryMapper;
    private final FinReportMappingMapper mappingMapper;
    private final FinBookMapper bookMapper;
    private final PartnerNameLookup partnerNameLookup;

    // ------------------------------------------------------------------
    // 资产负债表
    // ------------------------------------------------------------------

    @Override
    public BalanceSheetVO balanceSheet(String period, Long bookIdParam) {
        long bookId = resolveBookId(bookIdParam);
        YearMonth ym = parsePeriod(period);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        List<TrialBalanceRawVO> subjects = queryMapper.selectTrialBalanceRows(bookId, monthStart, monthEnd);
        List<FinReportMapping> mappings = mappings(TYPE_BS);

        // 损益类科目净额（未结转利润并入未分配利润行）
        BigDecimal plNetOpening = BigDecimal.ZERO;
        BigDecimal plNetClosing = BigDecimal.ZERO;
        for (TrialBalanceRawVO s : subjects) {
            if (CATEGORY_PL.equals(s.category())) {
                plNetOpening = plNetOpening.add(nz(s.openingCreditSum())).subtract(nz(s.openingDebitSum()));
                plNetClosing = plNetClosing.add(nz(s.openingCreditSum())).add(nz(s.periodCreditSum()))
                        .subtract(nz(s.openingDebitSum())).subtract(nz(s.periodDebitSum()));
            }
        }

        // 科目被映射行命中次数（0=未覆盖，>1=重叠，均产出口径提示）
        Map<Long, Integer> matchedCount = new HashMap<>();
        List<String> warnings = new ArrayList<>();

        BigDecimal assetOpen = BigDecimal.ZERO, assetClose = BigDecimal.ZERO;
        BigDecimal liabOpen = BigDecimal.ZERO, liabClose = BigDecimal.ZERO;
        BigDecimal eqOpen = BigDecimal.ZERO, eqClose = BigDecimal.ZERO;
        List<BalanceSheetVO.Row> rows = new ArrayList<>(mappings.size());

        for (FinReportMapping m : mappings) {
            String calc = blank(m.getCalcType());
            BigDecimal opening = BigDecimal.ZERO;
            BigDecimal closing = BigDecimal.ZERO;
            if (calc.isEmpty()) {
                List<String> codes = splitCodes(m.getSubjectCodes());
                for (TrialBalanceRawVO s : subjects) {
                    if (!matchesAny(s.code(), codes)) {
                        continue;
                    }
                    matchedCount.merge(s.subjectId(), 1, Integer::sum);
                    BigDecimal sOpenDebit = nz(s.openingDebitSum());
                    BigDecimal sOpenCredit = nz(s.openingCreditSum());
                    BigDecimal sCloseDebit = sOpenDebit.add(nz(s.periodDebitSum()));
                    BigDecimal sCloseCredit = sOpenCredit.add(nz(s.periodCreditSum()));
                    if (DIR_DEBIT.equals(m.getDirection())) {
                        opening = opening.add(sOpenDebit).subtract(sOpenCredit);
                        closing = closing.add(sCloseDebit).subtract(sCloseCredit);
                    } else {
                        opening = opening.add(sOpenCredit).subtract(sOpenDebit);
                        closing = closing.add(sCloseCredit).subtract(sCloseDebit);
                    }
                }
                if (m.getIncludePl() != null && m.getIncludePl() == 1) {
                    opening = opening.add(plNetOpening);
                    closing = closing.add(plNetClosing);
                }
                switch (blank(m.getSide())) {
                    case SIDE_ASSET -> {
                        assetOpen = assetOpen.add(opening);
                        assetClose = assetClose.add(closing);
                    }
                    case SIDE_LIABILITY -> {
                        liabOpen = liabOpen.add(opening);
                        liabClose = liabClose.add(closing);
                    }
                    case SIDE_EQUITY -> {
                        eqOpen = eqOpen.add(opening);
                        eqClose = eqClose.add(closing);
                    }
                    default -> warnings.add("行项目「%s」未配置 side，未计入任何合计".formatted(m.getItemName()));
                }
            } else {
                switch (calc) {
                    case "ASSET_TOTAL" -> {
                        opening = assetOpen;
                        closing = assetClose;
                    }
                    case "LIAB_TOTAL" -> {
                        opening = liabOpen;
                        closing = liabClose;
                    }
                    case "EQUITY_TOTAL" -> {
                        opening = eqOpen;
                        closing = eqClose;
                    }
                    case "LIAB_EQ_TOTAL" -> {
                        opening = liabOpen.add(eqOpen);
                        closing = liabClose.add(eqClose);
                    }
                    default -> warnings.add("未知计算行类型 %s（行「%s」）".formatted(calc, m.getItemName()));
                }
            }
            rows.add(new BalanceSheetVO.Row(m.getItemName(), blank(m.getSide()), blank(m.getDirection()),
                    calc, !calc.isEmpty(), blank(m.getSubjectCodes()), opening, closing));
        }

        // 未覆盖/重叠科目提示（期末余额非 0 才提示，避免噪音；
        // 损益类科目经未分配利润行 include_pl 并入，不算未覆盖）
        for (TrialBalanceRawVO s : subjects) {
            if (CATEGORY_PL.equals(s.category())) {
                continue;
            }
            int count = matchedCount.getOrDefault(s.subjectId(), 0);
            if (count == 0) {
                BigDecimal net = nz(s.openingDebitSum()).add(nz(s.periodDebitSum()))
                        .subtract(nz(s.openingCreditSum())).subtract(nz(s.periodCreditSum()));
                if (net.abs().compareTo(EPS) > 0) {
                    warnings.add("科目 %s %s 期末余额 %s 未被报表映射覆盖，两侧合计均不含该科目"
                            .formatted(s.code(), s.name(), net.toPlainString()));
                }
            } else if (count > 1) {
                warnings.add("科目 %s %s 被 %d 个行项目同时映射（重叠取数）".formatted(s.code(), s.name(), count));
            }
        }

        BigDecimal diff = assetClose.subtract(liabClose).subtract(eqClose);
        BigDecimal openingDiff = assetOpen.subtract(liabOpen).subtract(eqOpen);
        boolean balanced = diff.abs().compareTo(TOLERANCE) <= 0 && openingDiff.abs().compareTo(TOLERANCE) <= 0;
        boolean notCarried = plNetClosing.abs().compareTo(EPS) > 0;
        if (notCarried) {
            warnings.add("损益类科目期末净额 %s 未结转，已按现行余额并入未分配利润列示"
                    .formatted(plNetClosing.toPlainString()));
        }
        if (!balanced) {
            log.warn("[资产负债表][勾稽] 期间={} 账套={} 期末差额={} 期初差额={} 提示={}",
                    ym, bookId, diff.toPlainString(), openingDiff.toPlainString(), warnings);
        }
        return new BalanceSheetVO(ym.toString(), bookId, rows,
                assetClose, liabClose, eqClose, liabClose.add(eqClose),
                balanced, diff, openingDiff, notCarried, warnings);
    }

    // ------------------------------------------------------------------
    // 利润表
    // ------------------------------------------------------------------

    @Override
    public IncomeStatementVO incomeStatement(String period, LocalDate from, LocalDate to, Long bookIdParam) {
        long bookId = resolveBookId(bookIdParam);
        YearMonth ym = parsePeriod(period);
        LocalDate monthFrom = from != null ? from : ym.atDay(1);
        LocalDate monthTo = to != null ? to : ym.atEndOfMonth();
        if (monthFrom.isAfter(monthTo)) {
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.fin.range-order", monthFrom, monthTo);
        }
        LocalDate ytdFrom = monthFrom.withDayOfYear(1);
        LocalDate ytdTo = monthTo;

        List<TrialBalanceRawVO> monthAgg = queryMapper.selectTrialBalanceRows(bookId, monthFrom, monthTo);
        List<TrialBalanceRawVO> ytdAgg = ytdFrom.equals(monthFrom) && ytdTo.equals(monthTo)
                ? monthAgg : queryMapper.selectTrialBalanceRows(bookId, ytdFrom, ytdTo);
        List<FinReportMapping> mappings = mappings(TYPE_IS);

        Map<Long, Integer> matchedCount = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        // 运行合计：收入类(贷-借)+，成本费用类(借-贷)− → 依次得到营业利润/利润总额/净利润
        BigDecimal runningMonth = BigDecimal.ZERO;
        BigDecimal runningYtd = BigDecimal.ZERO;
        List<IncomeStatementVO.Row> rows = new ArrayList<>(mappings.size());
        BigDecimal netProfitMonth = BigDecimal.ZERO;
        BigDecimal netProfitYtd = BigDecimal.ZERO;

        for (FinReportMapping m : mappings) {
            String calc = blank(m.getCalcType());
            BigDecimal monthAmount;
            BigDecimal ytdAmount;
            if (calc.isEmpty()) {
                monthAmount = sumMapped(monthAgg, m, matchedCount);
                ytdAmount = sumMapped(ytdAgg, m, null);
                boolean credit = !DIR_DEBIT.equals(m.getDirection());
                runningMonth = runningMonth.add(credit ? monthAmount : monthAmount.negate());
                runningYtd = runningYtd.add(credit ? ytdAmount : ytdAmount.negate());
            } else {
                monthAmount = runningMonth;
                ytdAmount = runningYtd;
                if ("NET_PROFIT".equals(calc)) {
                    netProfitMonth = monthAmount;
                    netProfitYtd = ytdAmount;
                }
            }
            rows.add(new IncomeStatementVO.Row(m.getItemName(), blank(m.getDirection()), calc,
                    !calc.isEmpty(), blank(m.getSubjectCodes()), monthAmount, ytdAmount));
        }

        // 损益类科目覆盖检查：本年累计净额非 0 且未被映射 → 提示（净利润将不含该科目）
        for (TrialBalanceRawVO s : ytdAgg) {
            if (!CATEGORY_PL.equals(s.category()) || matchedCount.containsKey(s.subjectId())) {
                continue;
            }
            BigDecimal net = nz(s.periodDebitSum()).subtract(nz(s.periodCreditSum()));
            if (net.abs().compareTo(EPS) > 0) {
                warnings.add("损益科目 %s %s 本期净额 %s 未被利润表映射覆盖，净利润不含该科目"
                        .formatted(s.code(), s.name(), net.toPlainString()));
            }
        }
        return new IncomeStatementVO(ym.toString(), monthFrom.toString(), monthTo.toString(),
                ytdFrom.toString(), ytdTo.toString(), bookId, rows, netProfitMonth, netProfitYtd, warnings);
    }

    // ------------------------------------------------------------------
    // 应收 / 应付账龄
    // ------------------------------------------------------------------

    @Override
    public PartnerAgingVO arAging(LocalDate asOfParam, Long bookIdParam) {
        long bookId = resolveBookId(bookIdParam);
        LocalDate asOf = asOfParam == null ? LocalDate.now() : asOfParam;
        List<AgingRawVO> raws = queryMapper.selectUnsettledReceivableRows(bookId);
        return aging("RECEIVABLE", asOf, bookId, raws, true);
    }

    @Override
    public PartnerAgingVO apAging(LocalDate asOfParam, Long bookIdParam) {
        long bookId = resolveBookId(bookIdParam);
        LocalDate asOf = asOfParam == null ? LocalDate.now() : asOfParam;
        List<AgingRawVO> raws = queryMapper.selectUnsettledPayableRows(bookId);
        return aging("PAYABLE", asOf, bookId, raws, false);
    }

    /**
     * 账龄分桶聚合（应收/应付共用）。
     * <p>
     * 锚点 = COALESCE(biz_date, DATE(create_time))（单据日期口径，缺失回退创建日）；
     * asOf 之后立据的单据不计入；age = asOf − anchor，负数不会出现（已过滤未来单据）。
     */
    private PartnerAgingVO aging(String side, LocalDate asOf, long bookId,
                                 List<AgingRawVO> raws, boolean receivable) {
        String[] keys = {"D30", "D60", "D90", "D180", "D180P"};
        String[] labels = {"1-30天", "31-60天", "61-90天", "91-180天", "180天以上"};

        Map<Long, BigDecimal[]> byPartner = new LinkedHashMap<>();
        Map<Long, Long> docCount = new HashMap<>();
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        long totalDocs = 0;
        for (AgingRawVO raw : raws) {
            LocalDate anchor = raw.bizDate() != null ? raw.bizDate()
                    : raw.createTime() == null ? null : raw.createTime().toLocalDate();
            if (anchor == null || anchor.isAfter(asOf)) {
                continue; // 无任何日期的孤儿行不计入（口径：无法定位时点）
            }
            long age = java.time.temporal.ChronoUnit.DAYS.between(anchor, asOf);
            int idx = age <= 30 ? 0 : age <= 60 ? 1 : age <= 90 ? 2 : age <= 180 ? 3 : 4;
            BigDecimal unsettled = nz(raw.amount()).subtract(nz(raw.settledAmount()));
            BigDecimal[] buckets = byPartner.computeIfAbsent(raw.partnerId(), k -> new BigDecimal[5]);
            for (int i = 0; i < 5; i++) {
                if (buckets[i] == null) {
                    buckets[i] = BigDecimal.ZERO;
                }
            }
            buckets[idx] = buckets[idx].add(unsettled);
            docCount.merge(raw.partnerId(), 1L, Long::sum);
            totals.merge(raw.partnerId(), unsettled, BigDecimal::add);
            totalDocs++;
        }
        final BigDecimal grand = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, String> names = receivable
                ? partnerNameLookup.customerNames(byPartner.keySet())
                : partnerNameLookup.supplierNames(byPartner.keySet());

        List<PartnerAgingVO.Row> rows = new ArrayList<>(byPartner.size());
        byPartner.forEach((partnerId, buckets) -> rows.add(new PartnerAgingVO.Row(
                partnerId, names.get(partnerId), docCount.getOrDefault(partnerId, 0L),
                buckets[0], buckets[1], buckets[2], buckets[3], buckets[4],
                totals.get(partnerId), ratio(totals.get(partnerId), grand))));
        rows.sort(Comparator.comparing(PartnerAgingVO.Row::total).reversed()
                .thenComparing(PartnerAgingVO.Row::partnerId));

        PartnerAgingVO.Row totalRow = new PartnerAgingVO.Row(null, "合计", totalDocs,
                sum(rows, PartnerAgingVO.Row::d30), sum(rows, PartnerAgingVO.Row::d60),
                sum(rows, PartnerAgingVO.Row::d90), sum(rows, PartnerAgingVO.Row::d180),
                sum(rows, PartnerAgingVO.Row::d180p), grand, BigDecimal.ONE.setScale(4));

        List<PartnerAgingVO.Bucket> bucketList = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            BigDecimal amount = BigDecimal.ZERO;
            for (PartnerAgingVO.Row row : rows) {
                amount = amount.add(bucketOf(row, i));
            }
            bucketList.add(new PartnerAgingVO.Bucket(keys[i], labels[i], amount, ratio(amount, grand)));
        }
        return new PartnerAgingVO(side, asOf, bookId, bucketList, rows, totalRow, grand, totalDocs);
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /** 账套解析：显式 bookId 优先（供 CSV 等无法携带 X-Book-Id 头的场景），空=当前账套 */
    private long resolveBookId(Long bookIdParam) {
        if (bookIdParam == null) {
            return BookContext.getBookId();
        }
        if (bookIdParam <= 0) {
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.fin.bookid-positive");
        }
        FinBook book = bookMapper.selectById(bookIdParam);
        if (book == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.book-not-exists", bookIdParam);
        }
        return bookIdParam;
    }

    /** 期间解析：空=当月；格式非法抛业务异常 */
    private YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(period.trim());
        } catch (DateTimeParseException e) {
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.fin.period-format-ym", period);
        }
    }

    /** 读取指定报表类型的映射配置（按行序） */
    private List<FinReportMapping> mappings(String reportType) {
        List<FinReportMapping> list = mappingMapper.selectList(Wrappers.lambdaQuery(FinReportMapping.class)
                .eq(FinReportMapping::getReportType, reportType)
                .orderByAsc(FinReportMapping::getSortNo));
        if (list.isEmpty()) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.report-mapping-missing", reportType);
        }
        return list;
    }

    /** 汇总一行映射命中的科目净额（direction 决定借-贷 / 贷-借；matchedCount 非空时登记命中） */
    private BigDecimal sumMapped(List<TrialBalanceRawVO> subjects, FinReportMapping m,
                                 Map<Long, Integer> matchedCount) {
        List<String> codes = splitCodes(m.getSubjectCodes());
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (TrialBalanceRawVO s : subjects) {
            if (!matchesAny(s.code(), codes)) {
                continue;
            }
            if (matchedCount != null) {
                matchedCount.merge(s.subjectId(), 1, Integer::sum);
            }
            debit = debit.add(nz(s.periodDebitSum()));
            credit = credit.add(nz(s.periodCreditSum()));
        }
        return DIR_DEBIT.equals(m.getDirection()) ? debit.subtract(credit) : credit.subtract(debit);
    }

    /** 科目编码集合拆分（逗号分隔，去空白） */
    private static List<String> splitCodes(String subjectCodes) {
        if (subjectCodes == null || subjectCodes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(subjectCodes.split("[,，]"))
                .map(String::trim)
                .filter(c -> !c.isEmpty())
                .toList();
    }

    /** 前缀匹配：2221 命中 2221 / 222101 */
    private static boolean matchesAny(String subjectCode, List<String> codes) {
        if (codes.isEmpty()) {
            return false;
        }
        for (String code : codes) {
            if (subjectCode != null && subjectCode.startsWith(code)) {
                return true;
            }
        }
        return false;
    }

    private static BigDecimal bucketOf(PartnerAgingVO.Row row, int idx) {
        return switch (idx) {
            case 0 -> row.d30();
            case 1 -> row.d60();
            case 2 -> row.d90();
            case 3 -> row.d180();
            default -> row.d180p();
        };
    }

    private static BigDecimal sum(List<PartnerAgingVO.Row> rows,
                                  java.util.function.Function<PartnerAgingVO.Row, BigDecimal> getter) {
        return rows.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 占比 = 部分/总体（4 位 HALF_UP；总体 0 → 0） */
    private static BigDecimal ratio(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return part.divide(total, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
