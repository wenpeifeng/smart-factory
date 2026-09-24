package com.erp.asset.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.asset.dto.form.AssetForm;
import com.erp.asset.dto.form.DisposeForm;
import com.erp.asset.dto.request.AssetQuery;
import com.erp.asset.dto.vo.AssetVO;
import com.erp.asset.dto.vo.DisposeResultVO;
import com.erp.asset.entity.AstAsset;
import com.erp.asset.entity.AstCategory;
import com.erp.asset.entity.AstDepreciationDetail;
import com.erp.asset.entity.FinSubject;
import com.erp.asset.entity.FinVoucher;
import com.erp.asset.mapper.AstAssetMapper;
import com.erp.asset.mapper.AstCategoryMapper;
import com.erp.asset.mapper.AstDepreciationDetailMapper;
import com.erp.asset.mapper.AstSubjectMapper;
import com.erp.asset.mapper.AstVoucherMapper;
import com.erp.asset.service.AstAssetService;
import com.erp.asset.support.AssetBizNoGenerator;
import com.erp.asset.support.AssetNameLookup;
import com.erp.asset.support.AssetVoucherWriter;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 资产卡片服务实现。
 * <p>
 * 关键事务规则：
 * <ul>
 *   <li>新增：卡片 INSERT + 入账凭证（借 固定资产 / 贷 应付账款 2202，POSTED）同一事务；</li>
 *   <li>处置：净值转固定资产清理的平衡凭证 + 状态置 DISPOSED 同一事务；
 *       平衡关系：借(累计折旧 + 净值 + 清理收入) = 贷(原值 + 清理收入)；</li>
 *   <li>财务字段（类别/原值/残值/年限/入账日期）一旦计提过折旧即锁定，
 *       防止改动导致卡片与已生成凭证账实脱节。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AstAssetServiceImpl implements AstAssetService {

    /** 暂估应付科目编码（入账贷方；类别不映射应付科目，按企业会计准则固定 2202） */
    private static final String SUBJECT_AP = "2202";
    /** 库存现金科目编码（处置清理收入借方） */
    private static final String SUBJECT_CASH = "1001";
    /** 固定资产清理科目编码（处置净值转入方） */
    private static final String SUBJECT_CLEANUP = "1606";

    private final AstAssetMapper assetMapper;
    private final AstCategoryMapper categoryMapper;
    private final AstDepreciationDetailMapper detailMapper;
    private final AstSubjectMapper subjectMapper;
    private final AstVoucherMapper voucherMapper;
    private final AssetVoucherWriter voucherWriter;
    private final AssetBizNoGenerator bizNoGenerator;
    private final AssetNameLookup nameLookup;

    @Override
    public PageResult<AssetVO> page(AssetQuery query, long pageNum, long pageSize) {
        String keyword = query.keyword() == null ? null : query.keyword().trim();
        var wrapper = Wrappers.lambdaQuery(AstAsset.class)
                .eq(query.categoryId() != null, AstAsset::getCategoryId, query.categoryId())
                .eq(notBlank(query.status()), AstAsset::getStatus, query.status())
                .eq(query.deptId() != null, AstAsset::getDeptId, query.deptId())
                .and(notBlank(keyword), w -> w.like(AstAsset::getAssetCode, keyword)
                        .or().like(AstAsset::getName, keyword))
                .orderByDesc(AstAsset::getId);
        PageResult<AstAsset> page = PageUtils.of(assetMapper, wrapper, pageNum, pageSize);
        return new PageResult<>(batchToVO(page.getRecords()).stream().toList(),
                page.getTotal(), page.getPageNum(), page.getPageSize());
    }

    @Override
    public AssetVO detail(Long id) {
        return toVO(requireAsset(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AssetForm form) {
        AstCategory category = requireCategory(form.categoryId());
        if (form.originalValue() == null) {
            throw BusinessException.ofKey("erp.asset.original-value-required");
        }
        // 残值默认 = 原值 × 类别残值率；年限默认取类别政策；入账日期默认当天
        BigDecimal residual = form.residualValue() != null
                ? form.residualValue()
                : form.originalValue().multiply(category.getResidualRate())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        Integer lifeMonths = form.expectedLifeMonths() != null
                ? form.expectedLifeMonths() : category.getExpectedLifeMonths();
        LocalDate accountDate = form.accountDate() != null ? form.accountDate() : LocalDate.now();
        if (residual.compareTo(form.originalValue()) > 0) {
            throw BusinessException.ofKey("erp.asset.residual-over-original2", residual.toPlainString(), form.originalValue().toPlainString());
        }

        AstAsset asset = new AstAsset();
        asset.setAssetCode(bizNoGenerator.nextAssetCode());
        asset.setName(form.name().trim());
        asset.setCategoryId(category.getId());
        asset.setSpec(form.spec());
        asset.setSupplierId(form.supplierId());
        asset.setLocation(form.location());
        asset.setDeptId(form.deptId());
        asset.setKeeper(form.keeper());
        asset.setOriginalValue(form.originalValue());
        asset.setResidualValue(residual);
        asset.setExpectedLifeMonths(lifeMonths);
        asset.setDepreciatedMonths(0);
        asset.setAccumulatedDepreciation(BigDecimal.ZERO);
        asset.setAccountDate(accountDate);
        asset.setStatus(AstAsset.STATUS_IN_USE);
        asset.setRemark(form.remark());
        assetMapper.insert(asset);

        // 入账凭证：借 固定资产(类别资产科目) / 贷 应付账款-暂估(2202)，生成即过账
        FinSubject assetSubject = voucherWriter.requireSubject(category.getAssetSubjectId());
        FinSubject apSubject = voucherWriter.requireSubjectByCode(SUBJECT_AP);
        String summary = "资产入账 " + asset.getAssetCode();
        Long voucherId = voucherWriter.createPostedVoucher(accountDate, List.of(
                new AssetVoucherWriter.EntrySpec(assetSubject.getId(), summary,
                        form.originalValue(), BigDecimal.ZERO),
                new AssetVoucherWriter.EntrySpec(apSubject.getId(), summary,
                        BigDecimal.ZERO, form.originalValue())),
                "固定资产入账自动凭证：" + asset.getAssetCode() + " " + asset.getName());

        asset.setEntryVoucherId(voucherId);
        assetMapper.updateById(asset);
        return asset.getId();
    }

    @Override
    public void update(Long id, AssetForm form) {
        AstAsset asset = requireAsset(id);
        if (AstAsset.STATUS_DISPOSED.equals(asset.getStatus())) {
            throw BusinessException.ofKey("erp.asset.disposed-no-modify");
        }
        // 已计提折旧 → 财务字段锁定（改动会破坏已生成凭证的账实一致）
        boolean locked = asset.getAccumulatedDepreciation().signum() > 0
                || (asset.getDepreciatedMonths() != null && asset.getDepreciatedMonths() > 0);
        if (locked) {
            requireUnchanged("类别", asset.getCategoryId(), form.categoryId());
            requireUnchanged("原值", asset.getOriginalValue(), form.originalValue());
            requireUnchanged("残值", asset.getResidualValue(), form.residualValue());
            requireUnchanged("预计年限月", asset.getExpectedLifeMonths(), form.expectedLifeMonths());
            requireUnchanged("入账日期", asset.getAccountDate(), form.accountDate());
        }
        // categoryId 为必填：换类别时校验新类别有效
        requireCategory(form.categoryId());

        if (form.residualValue() != null && form.originalValue() != null
                && form.residualValue().compareTo(form.originalValue()) > 0) {
            throw BusinessException.ofKey("erp.asset.residual-over-original3");
        }

        AstAsset update = new AstAsset();
        update.setId(id);
        update.setName(form.name().trim());
        update.setCategoryId(form.categoryId());
        update.setSpec(form.spec());
        update.setSupplierId(form.supplierId());
        update.setLocation(form.location());
        update.setDeptId(form.deptId());
        update.setKeeper(form.keeper());
        update.setRemark(form.remark());
        // 财务字段：未锁定时才允许随表单变更
        if (!locked) {
            if (form.originalValue() != null) {
                update.setOriginalValue(form.originalValue());
            }
            if (form.residualValue() != null) {
                update.setResidualValue(form.residualValue());
            }
            if (form.expectedLifeMonths() != null) {
                update.setExpectedLifeMonths(form.expectedLifeMonths());
            }
            if (form.accountDate() != null) {
                update.setAccountDate(form.accountDate());
            }
        }
        // 状态仅允许 在用/闲置 互转（处置必须走 dispose 接口）
        if (notBlank(form.status())) {
            if (AstAsset.STATUS_DISPOSED.equals(form.status())) {
                throw BusinessException.ofKey("erp.asset.dispose-via-api");
            }
            update.setStatus(form.status());
        }
        assetMapper.updateById(update);
    }

    @Override
    public void delete(Long id) {
        AstAsset asset = requireAsset(id);
        if (AstAsset.STATUS_DISPOSED.equals(asset.getStatus())) {
            throw BusinessException.ofKey("erp.asset.disposed-no-delete");
        }
        if (asset.getAccumulatedDepreciation().signum() > 0 || asset.getDepreciatedMonths() > 0) {
            throw BusinessException.ofKey("erp.asset.depreciated-no-delete");
        }
        Long details = detailMapper.selectCount(Wrappers.lambdaQuery(AstDepreciationDetail.class)
                .eq(AstDepreciationDetail::getAssetId, id));
        if (details != null && details > 0) {
            throw BusinessException.ofKey("erp.asset.has-dep-details-no-delete");
        }
        // 入账凭证为 POSTED 历史凭证，保留不随卡片删除（见模块文档"遗留问题"）
        assetMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DisposeResultVO dispose(Long id, DisposeForm form) {
        AstAsset asset = requireAsset(id);
        if (AstAsset.STATUS_DISPOSED.equals(asset.getStatus())) {
            throw BusinessException.ofKey("erp.asset.already-disposed");
        }
        AstCategory category = requireCategory(asset.getCategoryId());

        BigDecimal original = asset.getOriginalValue();
        BigDecimal accumulated = nz(asset.getAccumulatedDepreciation());
        BigDecimal net = original.subtract(accumulated);
        BigDecimal income = nz(form.income());

        // 处置凭证（借贷平衡）：
        //   借 累计折旧(已提额) + 借 固定资产清理(净值) / 贷 固定资产(原值)
        //   清理收入>0 追加：借 库存现金 / 贷 固定资产清理
        FinSubject assetSubject = voucherWriter.requireSubject(category.getAssetSubjectId());
        FinSubject accumulatedSubject = voucherWriter
                .requireSubject(category.getAccumulatedDepreciationSubjectId());
        FinSubject cleanupSubject = voucherWriter.requireSubjectByCode(SUBJECT_CLEANUP);
        FinSubject cashSubject = income.signum() > 0
                ? voucherWriter.requireSubjectByCode(SUBJECT_CASH) : null;

        String summary = "资产处置 " + asset.getAssetCode();
        java.util.List<AssetVoucherWriter.EntrySpec> entries = new java.util.ArrayList<>();
        if (accumulated.signum() > 0) {
            entries.add(new AssetVoucherWriter.EntrySpec(accumulatedSubject.getId(),
                    summary + " 转出累计折旧", accumulated, BigDecimal.ZERO));
        }
        entries.add(new AssetVoucherWriter.EntrySpec(cleanupSubject.getId(),
                summary + " 净值转清理", net, BigDecimal.ZERO));
        entries.add(new AssetVoucherWriter.EntrySpec(assetSubject.getId(),
                summary + " 转出原值", BigDecimal.ZERO, original));
        if (income.signum() > 0) {
            entries.add(new AssetVoucherWriter.EntrySpec(cashSubject.getId(),
                    summary + " 清理收入", income, BigDecimal.ZERO));
            entries.add(new AssetVoucherWriter.EntrySpec(cleanupSubject.getId(),
                    summary + " 清理收入", BigDecimal.ZERO, income));
        }
        String remark = form.remark() == null || form.remark().isBlank()
                ? "固定资产处置自动凭证：" + asset.getAssetCode()
                : "固定资产处置自动凭证：" + asset.getAssetCode() + " " + form.remark().trim();
        Long voucherId = voucherWriter.createPostedVoucher(LocalDate.now(), entries, remark);

        AstAsset update = new AstAsset();
        update.setId(id);
        update.setStatus(AstAsset.STATUS_DISPOSED);
        update.setDisposeMethod(form.method());
        update.setDisposeIncome(income);
        update.setDisposeTime(LocalDateTime.now());
        update.setDisposeVoucherId(voucherId);
        assetMapper.updateById(update);

        return new DisposeResultVO(id, asset.getAssetCode(), net, income, voucherId,
                voucherNo(voucherId));
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /** 实体 → VO（单条，详情场景） */
    private AssetVO toVO(AstAsset asset) {
        return batchToVO(List.of(asset)).get(0);
    }

    /** 批量实体 → VO（类别/部门/供应商名称各一次批量查询，避免 N+1） */
    private List<AssetVO> batchToVO(List<AstAsset> assets) {
        List<Long> categoryIds = assets.stream().map(AstAsset::getCategoryId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> categoryNames = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(AstCategory::getId, AstCategory::getName));
        Map<Long, String> deptNames = nameLookup.deptNames(
                assets.stream().map(AstAsset::getDeptId).filter(Objects::nonNull).distinct().toList());
        Map<Long, String> supplierNames = nameLookup.supplierNames(
                assets.stream().map(AstAsset::getSupplierId).filter(Objects::nonNull).distinct().toList());
        return assets.stream()
                .map(a -> AssetVO.from(a,
                        a.getCategoryId() == null ? null : categoryNames.get(a.getCategoryId()),
                        a.getDeptId() == null ? null : deptNames.get(a.getDeptId()),
                        a.getSupplierId() == null ? null : supplierNames.get(a.getSupplierId())))
                .toList();
    }

    /**
     * 已锁定财务字段被改动时抛异常（未提供的字段视为不变）。
     * <p>
     * 金额用 compareTo 比较：JSON 反序列化的 60000 与数据库读出的 60000.00 尺度不同，
     * 用 equals 会误判为"已变更"（DECIMAL 精确比较禁 equals 的同一陷阱）。
     */
    private void requireUnchanged(String field, Object current, Object provided) {
        if (provided == null) {
            return;
        }
        boolean changed = current instanceof BigDecimal currentAmount
                ? currentAmount.compareTo((BigDecimal) provided) != 0
                : !provided.equals(current);
        if (changed) {
            throw BusinessException.ofKey("erp.asset.depreciated-locked", field, current);
        }
    }

    /** 资产必须存在 */
    private AstAsset requireAsset(Long id) {
        AstAsset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.asset.asset-not-exists", id);
        }
        return asset;
    }

    /** 类别必须存在 */
    private AstCategory requireCategory(Long id) {
        AstCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.asset.category-not-exists", id);
        }
        return category;
    }

    /** 凭证 ID → 凭证号 */
    private String voucherNo(Long voucherId) {
        if (voucherId == null) {
            return null;
        }
        FinVoucher voucher = voucherMapper.selectById(voucherId);
        return voucher == null ? null : voucher.getVoucherNo();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
