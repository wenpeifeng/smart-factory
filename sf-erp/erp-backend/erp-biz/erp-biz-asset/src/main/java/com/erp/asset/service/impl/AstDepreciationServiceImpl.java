package com.erp.asset.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.asset.dto.vo.CategoryStatsRawVO;
import com.erp.asset.dto.vo.DepreciationDetailVO;
import com.erp.asset.dto.vo.DepreciationPeriodDetailVO;
import com.erp.asset.dto.vo.DepreciationPeriodVO;
import com.erp.asset.dto.vo.DepreciationRunVO;
import com.erp.asset.dto.vo.DeptStatsRawVO;
import com.erp.asset.dto.vo.StatisticsRowVO;
import com.erp.asset.dto.vo.StatisticsVO;
import com.erp.asset.entity.AstAsset;
import com.erp.asset.entity.AstCategory;
import com.erp.asset.entity.AstDepreciation;
import com.erp.asset.entity.AstDepreciationDetail;
import com.erp.asset.entity.FinVoucher;
import com.erp.asset.mapper.AstAssetMapper;
import com.erp.asset.mapper.AstCategoryMapper;
import com.erp.asset.mapper.AstDepreciationDetailMapper;
import com.erp.asset.mapper.AstDepreciationMapper;
import com.erp.asset.mapper.AstQueryMapper;
import com.erp.asset.mapper.AstVoucherMapper;
import com.erp.asset.service.AstDepreciationService;
import com.erp.asset.support.AssetNameLookup;
import com.erp.asset.support.AssetVoucherWriter;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 折旧计提服务实现。
 * <p>
 * run 的防重与并发控制（三重保障）：
 * <ol>
 *   <li>uk_period：同一期间至多一行；</li>
 *   <li>SELECT ... FOR UPDATE：事务内对期间行加锁，串行化同期间并发 run；</li>
 *   <li>CLOSED 状态 + 条件 UPDATE（WHERE status='OPEN'）：已计提期间重复运行直接拒绝。</li>
 * </ol>
 * 计提与资产回写同一事务：凭证、明细、资产累计折旧/已提月数、期间关闭要么全部成功要么整体回滚。
 */
@Service
@RequiredArgsConstructor
public class AstDepreciationServiceImpl implements AstDepreciationService {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final AstDepreciationMapper depreciationMapper;
    private final AstDepreciationDetailMapper detailMapper;
    private final AstAssetMapper assetMapper;
    private final AstCategoryMapper categoryMapper;
    private final AstVoucherMapper voucherMapper;
    private final AstQueryMapper queryMapper;
    private final AssetVoucherWriter voucherWriter;
    private final AssetNameLookup nameLookup;

    @Override
    public PageResult<DepreciationPeriodVO> page(long pageNum, long pageSize) {
        PageResult<AstDepreciation> page = PageUtils.of(depreciationMapper,
                Wrappers.lambdaQuery(AstDepreciation.class).orderByDesc(AstDepreciation::getPeriod),
                pageNum, pageSize);
        Map<Long, String> voucherNos = voucherNos(
                page.getRecords().stream().map(AstDepreciation::getVoucherId).toList());
        List<DepreciationPeriodVO> records = page.getRecords().stream()
                .map(p -> new DepreciationPeriodVO(p.getId(), p.getPeriod(), p.getStatus(),
                        p.getVoucherId(),
                        p.getVoucherId() == null ? null : voucherNos.get(p.getVoucherId()),
                        p.getAssetCount(), p.getTotalAmount(), p.getCreateTime(), p.getUpdateTime()))
                .toList();
        return new PageResult<>(records, page.getTotal(), page.getPageNum(), page.getPageSize());
    }

    @Override
    public DepreciationPeriodDetailVO detail(String period) {
        requirePeriod(period);
        AstDepreciation head = depreciationMapper.selectOne(
                Wrappers.lambdaQuery(AstDepreciation.class).eq(AstDepreciation::getPeriod, period));
        if (head == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.asset.period-not-depreciated", period);
        }
        List<AstDepreciationDetail> details = detailMapper.selectList(
                Wrappers.lambdaQuery(AstDepreciationDetail.class)
                        .eq(AstDepreciationDetail::getPeriodId, head.getId())
                        .orderByAsc(AstDepreciationDetail::getId));

        // 批量回显：资产编码/名称、类别名、部门名（0 资产期间直接空明细，跳过空集合 IN () 查询）
        List<Long> assetIds = details.stream().map(AstDepreciationDetail::getAssetId).distinct().toList();
        Map<Long, AstAsset> assets = assetIds.isEmpty() ? Map.of()
                : assetMapper.selectBatchIds(assetIds).stream()
                        .collect(Collectors.toMap(AstAsset::getId, a -> a));
        List<Long> categoryIds = assets.values().stream().map(AstAsset::getCategoryId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> categoryNames = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(AstCategory::getId, AstCategory::getName));
        Map<Long, String> deptNames = nameLookup.deptNames(
                assets.values().stream().map(AstAsset::getDeptId).filter(Objects::nonNull).distinct().toList());

        List<DepreciationDetailVO> rows = details.stream().map(d -> {
            AstAsset asset = assets.get(d.getAssetId());
            return new DepreciationDetailVO(d.getId(), d.getAssetId(),
                    asset == null ? null : asset.getAssetCode(),
                    asset == null ? null : asset.getName(),
                    asset == null || asset.getCategoryId() == null ? null
                            : categoryNames.get(asset.getCategoryId()),
                    asset == null || asset.getDeptId() == null ? null
                            : deptNames.get(asset.getDeptId()),
                    d.getMonthAmount(), d.getVoucherId());
        }).toList();
        return new DepreciationPeriodDetailVO(head.getId(), head.getPeriod(), head.getStatus(),
                head.getVoucherId(),
                head.getVoucherId() == null ? null
                        : voucherNos(List.of(head.getVoucherId())).get(head.getVoucherId()),
                head.getAssetCount(), head.getTotalAmount(), rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepreciationRunVO run(String period) {
        YearMonth ym = requirePeriod(period);

        // 期间懒创建（uk_period 防重）+ FOR UPDATE 行锁串行化同期间并发
        AstDepreciation head = depreciationMapper.selectOne(
                Wrappers.lambdaQuery(AstDepreciation.class).eq(AstDepreciation::getPeriod, period));
        if (head == null) {
            AstDepreciation created = new AstDepreciation();
            created.setPeriod(period);
            created.setStatus(AstDepreciation.STATUS_OPEN);
            created.setAssetCount(0);
            created.setTotalAmount(BigDecimal.ZERO);
            try {
                depreciationMapper.insert(created);
            } catch (DuplicateKeyException e) {
                // 并发对手已插入，走下面的行锁读取
            }
        }
        AstDepreciation locked = queryMapper.selectPeriodForUpdate(period);
        if (locked == null) {
            throw BusinessException.ofKey("erp.asset.period-init-failed", period);
        }
        if (AstDepreciation.STATUS_CLOSED.equals(locked.getStatus())) {
            throw BusinessException.ofKey("erp.asset.period-closed-no-rerun", period);
        }

        // 待计提资产：在用 且 已提月数 < 预计年限
        List<AstAsset> assets = assetMapper.selectList(Wrappers.lambdaQuery(AstAsset.class)
                .eq(AstAsset::getStatus, AstAsset.STATUS_IN_USE)
                .apply("depreciated_months < expected_life_months")
                .orderByAsc(AstAsset::getId));
        if (assets.isEmpty()) {
            closePeriod(locked.getId(), null, 0, BigDecimal.ZERO);
            return new DepreciationRunVO(period, 0, BigDecimal.ZERO, null, null,
                    "期间[%s]无符合条件的资产（在用且未提满），期间已关闭".formatted(period));
        }
        Map<Long, AstCategory> categories = categoryMapper.selectBatchIds(
                assets.stream().map(AstAsset::getCategoryId).distinct().toList())
                .stream().collect(Collectors.toMap(AstCategory::getId, c -> c));
        Map<Long, String> deptNames = nameLookup.deptNames(
                assets.stream().map(AstAsset::getDeptId).filter(Objects::nonNull).distinct().toList());

        // 逐资产计算月折旧额，并按（部门 × 折旧费用科目）汇总借方、按累计折旧科目汇总贷方
        BigDecimal total = BigDecimal.ZERO;
        Map<Long, BigDecimal> monthAmounts = new LinkedHashMap<>();
        Map<String, List<Object[]>> debitGroups = new LinkedHashMap<>();
        Map<Long, BigDecimal> creditGroups = new LinkedHashMap<>();
        for (AstAsset asset : assets) {
            AstCategory category = categories.get(asset.getCategoryId());
            if (category == null) {
                throw BusinessException.ofKey("erp.asset.asset-category-not-exists", asset.getAssetCode(), asset.getCategoryId());
            }
            BigDecimal monthly = monthlyDepreciation(asset);
            total = total.add(monthly);
            monthAmounts.put(asset.getId(), monthly);
            // 借方分组键：部门ID|费用科目ID（同部门多科目时自动分行，满足"按部门汇总多行"）
            String debitKey = asset.getDeptId() + "|" + category.getDepreciationExpenseSubjectId();
            debitGroups.computeIfAbsent(debitKey, k -> new ArrayList<>())
                    .add(new Object[]{asset.getDeptId(), category.getDepreciationExpenseSubjectId(), monthly});
            creditGroups.merge(category.getAccumulatedDepreciationSubjectId(), monthly, BigDecimal::add);
        }

        // 汇总凭证分录：借 折旧费用（部门×科目 分组多行）/ 贷 累计折旧
        List<AssetVoucherWriter.EntrySpec> entries = new ArrayList<>();
        debitGroups.forEach((key, rows) -> {
            Long deptId = (Long) rows.get(0)[0];
            Long subjectId = (Long) rows.get(0)[1];
            BigDecimal amount = rows.stream().map(r -> (BigDecimal) r[2])
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String deptLabel = deptId == null ? "未分配部门"
                    : deptNames.getOrDefault(deptId, "部门#" + deptId);
            entries.add(new AssetVoucherWriter.EntrySpec(subjectId,
                    "折旧计提 " + period + " " + deptLabel, amount, BigDecimal.ZERO));
        });
        creditGroups.forEach((subjectId, amount) -> entries.add(
                new AssetVoucherWriter.EntrySpec(subjectId, "折旧计提 " + period + " 累计折旧",
                        BigDecimal.ZERO, amount)));
        Long voucherId = voucherWriter.createPostedVoucher(ym.atEndOfMonth(), entries,
                "折旧计提自动凭证：期间 " + period + "，共 " + assets.size() + " 项资产");

        // 资产回写（条件 UPDATE 兜底并发：处置/提满/删除并发时影响 0 行即整体回滚）
        for (AstAsset asset : assets) {
            int affected = assetMapper.applyDepreciation(asset.getId(), monthAmounts.get(asset.getId()));
            if (affected != 1) {
                throw BusinessException.ofKey("erp.asset.asset-status-concurrent-change", asset.getAssetCode());
            }
        }
        // 折旧明细（同期间共用汇总凭证）
        for (AstAsset asset : assets) {
            AstDepreciationDetail detail = new AstDepreciationDetail();
            detail.setPeriodId(locked.getId());
            detail.setAssetId(asset.getId());
            detail.setMonthAmount(monthAmounts.get(asset.getId()));
            detail.setVoucherId(voucherId);
            detailMapper.insert(detail);
        }
        closePeriod(locked.getId(), voucherId, assets.size(), total);

        FinVoucher voucher = voucherMapper.selectById(voucherId);
        return new DepreciationRunVO(period, assets.size(), total, voucherId,
                voucher == null ? null : voucher.getVoucherNo(), null);
    }

    @Override
    public StatisticsVO statistics(String period) {
        String resolved = (period == null || period.isBlank())
                ? YearMonth.now().format(PERIOD_FORMAT) : period;
        YearMonth ym = requirePeriod(resolved);
        LocalDate periodEnd = ym.atEndOfMonth();

        List<DeptStatsRawVO> deptRows = queryMapper.selectStatsByDept(resolved, periodEnd.toString());
        List<CategoryStatsRawVO> categoryRows = queryMapper.selectStatsByCategory(resolved, periodEnd.toString());

        Map<Long, String> deptNames = nameLookup.deptNames(
                deptRows.stream().map(DeptStatsRawVO::deptId).filter(Objects::nonNull).toList());
        List<Long> categoryIds = categoryRows.stream().map(CategoryStatsRawVO::categoryId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> categoryNames = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(AstCategory::getId, AstCategory::getName));

        List<StatisticsRowVO> byDept = deptRows.stream()
                .map(r -> new StatisticsRowVO(r.deptId(),
                        r.deptId() == null ? "未分配部门"
                                : deptNames.getOrDefault(r.deptId(), "部门#" + r.deptId()),
                        nz(r.originalSum()), nz(r.depSum()), nz(r.netSum())))
                .toList();
        List<StatisticsRowVO> byCategory = categoryRows.stream()
                .map(r -> new StatisticsRowVO(r.categoryId(),
                        r.categoryId() == null ? "未知类别"
                                : categoryNames.getOrDefault(r.categoryId(), "类别#" + r.categoryId()),
                        nz(r.originalSum()), nz(r.depSum()), nz(r.netSum())))
                .toList();

        // 合计口径与 byDept 一致（未删除 + 未处置 + 入账 ≤ 期间末 的全部资产）
        BigDecimal originalTotal = byDept.stream().map(StatisticsRowVO::originalValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal depTotal = byDept.stream().map(StatisticsRowVO::accumulatedDepreciation)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StatisticsVO(resolved, byDept, byCategory,
                new StatisticsVO.Total(originalTotal, depTotal, originalTotal.subtract(depTotal)));
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
     * 月折旧额 =（原值 − 残值）/ 预计年限月，HALF_UP 保留 2 位。
     */
    private BigDecimal monthlyDepreciation(AstAsset asset) {
        BigDecimal base = nz(asset.getOriginalValue()).subtract(nz(asset.getResidualValue()));
        if (base.signum() < 0) {
            throw BusinessException.ofKey("erp.asset.residual-over-original", asset.getAssetCode());
        }
        if (base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return base.divide(BigDecimal.valueOf(asset.getExpectedLifeMonths()), 2, RoundingMode.HALF_UP);
    }

    /** 期间关闭（条件 UPDATE：仅 OPEN 可关，防并发重复关闭） */
    private void closePeriod(Long periodId, Long voucherId, int assetCount, BigDecimal totalAmount) {
        int affected = depreciationMapper.update(null,
                Wrappers.lambdaUpdate(AstDepreciation.class)
                        .eq(AstDepreciation::getId, periodId)
                        .eq(AstDepreciation::getStatus, AstDepreciation.STATUS_OPEN)
                        .set(AstDepreciation::getStatus, AstDepreciation.STATUS_CLOSED)
                        .set(voucherId != null, AstDepreciation::getVoucherId, voucherId)
                        .set(AstDepreciation::getAssetCount, assetCount)
                        .set(AstDepreciation::getTotalAmount, totalAmount));
        if (affected != 1) {
            throw BusinessException.ofKey("erp.asset.dep-period-concurrent-change");
        }
    }

    /** 期间格式校验：6 位数字 yyyyMM */
    private YearMonth requirePeriod(String period) {
        if (period == null || !period.matches("^\\d{6}$")) {
            throw BusinessException.ofKey("erp.asset.period-format-invalid", period);
        }
        try {
            return YearMonth.parse(period, PERIOD_FORMAT);
        } catch (DateTimeParseException e) {
            throw BusinessException.ofKey("erp.asset.period-invalid", period);
        }
    }

    /** 凭证 ID 集合 → id → 凭证号 */
    private Map<Long, String> voucherNos(List<Long> voucherIds) {
        List<Long> ids = voucherIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return voucherMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(FinVoucher::getId, FinVoucher::getVoucherNo));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
