package com.erp.asset.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.asset.dto.form.CategoryForm;
import com.erp.asset.dto.vo.CategoryVO;
import com.erp.asset.entity.AstAsset;
import com.erp.asset.entity.AstCategory;
import com.erp.asset.entity.FinSubject;
import com.erp.asset.mapper.AstAssetMapper;
import com.erp.asset.mapper.AstCategoryMapper;
import com.erp.asset.mapper.AstSubjectMapper;
import com.erp.asset.service.AstCategoryService;
import com.erp.asset.support.AssetVoucherWriter;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 资产类别服务实现。
 * <p>
 * 科目映射三科目在新增/修改时全部做"存在 + 启用 + 末级"校验，
 * 保证类别一旦保存即可用于自动凭证生成。
 */
@Service
@RequiredArgsConstructor
public class AstCategoryServiceImpl implements AstCategoryService {

    private final AstCategoryMapper categoryMapper;
    private final AstAssetMapper assetMapper;
    private final AstSubjectMapper subjectMapper;
    private final AssetVoucherWriter voucherWriter;

    @Override
    public List<CategoryVO> list() {
        List<AstCategory> categories = categoryMapper.selectList(
                Wrappers.lambdaQuery(AstCategory.class).orderByAsc(AstCategory::getCode));
        // 一次性回填三个科目的编码/名称展示
        Map<Long, FinSubject> subjects = subjectMap(categories.stream()
                .flatMap(c -> java.util.stream.Stream.of(c.getAssetSubjectId(),
                        c.getAccumulatedDepreciationSubjectId(), c.getDepreciationExpenseSubjectId()))
                .distinct().toList());
        return categories.stream()
                .map(c -> CategoryVO.from(c, subjects.get(c.getAssetSubjectId()),
                        subjects.get(c.getAccumulatedDepreciationSubjectId()),
                        subjects.get(c.getDepreciationExpenseSubjectId())))
                .toList();
    }

    @Override
    public Long create(CategoryForm form) {
        requireCodeFree(form.code(), null);
        validateSubjects(form);
        AstCategory category = new AstCategory();
        applyForm(category, form);
        categoryMapper.insert(category);
        return category.getId();
    }

    @Override
    public void update(Long id, CategoryForm form) {
        requireCategory(id);
        requireCodeFree(form.code(), id);
        validateSubjects(form);
        AstCategory category = new AstCategory();
        category.setId(id);
        applyForm(category, form);
        categoryMapper.updateById(category);
    }

    @Override
    public void delete(Long id) {
        requireCategory(id);
        Long used = assetMapper.selectCount(Wrappers.lambdaQuery(AstAsset.class)
                .eq(AstAsset::getCategoryId, id));
        if (used != null && used > 0) {
            throw BusinessException.ofKey("erp.asset.category-has-assets", used);
        }
        categoryMapper.deleteById(id);
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /** 编码唯一性校验（update 时排除自身） */
    private void requireCodeFree(String code, Long excludeId) {
        AstCategory existing = categoryMapper.selectOne(
                Wrappers.lambdaQuery(AstCategory.class).eq(AstCategory::getCode, code).last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw BusinessException.ofKey("erp.asset.category-code-exists", code);
        }
    }

    /** 三个科目映射全部校验（存在 + 启用 + 末级） */
    private void validateSubjects(CategoryForm form) {
        voucherWriter.requireSubject(form.assetSubjectId());
        voucherWriter.requireSubject(form.accumulatedDepreciationSubjectId());
        voucherWriter.requireSubject(form.depreciationExpenseSubjectId());
    }

    /** 表单字段覆盖到实体 */
    private void applyForm(AstCategory category, CategoryForm form) {
        category.setCode(form.code().trim());
        category.setName(form.name().trim());
        category.setDepreciationMethod(form.depreciationMethod());
        category.setExpectedLifeMonths(form.expectedLifeMonths());
        category.setResidualRate(form.residualRate());
        category.setAssetSubjectId(form.assetSubjectId());
        category.setAccumulatedDepreciationSubjectId(form.accumulatedDepreciationSubjectId());
        category.setDepreciationExpenseSubjectId(form.depreciationExpenseSubjectId());
        category.setRemark(form.remark());
    }

    /** 类别必须存在 */
    private AstCategory requireCategory(Long id) {
        AstCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.asset.category-not-exists", id);
        }
        return category;
    }

    /** 科目 ID 集合 → id -> 实体 */
    private Map<Long, FinSubject> subjectMap(List<Long> subjectIds) {
        if (subjectIds == null || subjectIds.isEmpty()) {
            return Map.of();
        }
        return subjectMapper.selectBatchIds(subjectIds).stream()
                .collect(Collectors.toMap(FinSubject::getId, Function.identity()));
    }
}
