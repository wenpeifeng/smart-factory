package com.erp.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.dto.form.BaseMaterialCategoryForm;
import com.erp.basedata.dto.vo.BaseMaterialCategoryVO;
import com.erp.basedata.entity.BaseMaterial;
import com.erp.basedata.entity.BaseMaterialCategory;
import com.erp.basedata.mapper.BaseMaterialCategoryMapper;
import com.erp.basedata.mapper.BaseMaterialMapper;
import com.erp.basedata.service.BaseMaterialCategoryService;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 物料分类 Service 实现（树形）。
 * <p>分类量级小（百级），树查询/子孙收集均一次取全表后在内存组装，避免递归 SQL 的 N+1 查询。
 */
@Service
@RequiredArgsConstructor
public class BaseMaterialCategoryServiceImpl implements BaseMaterialCategoryService {

    private final BaseMaterialCategoryMapper categoryMapper;
    private final BaseMaterialMapper materialMapper;

    @Override
    public List<BaseMaterialCategoryVO> tree() {
        // 一次取全表，按父 ID 分组后在内存递归挂 children
        Map<Long, List<BaseMaterialCategory>> byParent = categoryMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(BaseMaterialCategory::getParentId));
        return assembleChildren(0L, byParent);
    }

    /** 递归组装 parentId 下所有子节点（按 sort,id 升序） */
    private List<BaseMaterialCategoryVO> assembleChildren(Long parentId,
                                                          Map<Long, List<BaseMaterialCategory>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(entity -> {
                    BaseMaterialCategoryVO vo = BaseMaterialCategoryVO.from(entity);
                    vo.setChildren(assembleChildren(entity.getId(), byParent));
                    return vo;
                })
                .sorted(Comparator.comparing(BaseMaterialCategoryVO::getSort)
                        .thenComparing(BaseMaterialCategoryVO::getId))
                .toList();
    }

    @Override
    public BaseMaterialCategory getById(Long id) {
        BaseMaterialCategory entity = categoryMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.material-category-not-exists", id);
        }
        return entity;
    }

    @Override
    public Long create(BaseMaterialCategoryForm form) {
        checkCodeUnique(form.getCode().trim(), null);
        checkParent(form.getParentId(), null);
        BaseMaterialCategory entity = new BaseMaterialCategory();
        applyForm(entity, form);
        categoryMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, BaseMaterialCategoryForm form) {
        BaseMaterialCategory entity = getById(id);
        checkCodeUnique(form.getCode().trim(), id);
        checkParent(form.getParentId(), id);
        applyForm(entity, form);
        categoryMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        getById(id);
        // 存在子分类禁删（树完整性）
        Long childCount = categoryMapper.selectCount(Wrappers.lambdaQuery(BaseMaterialCategory.class)
                .eq(BaseMaterialCategory::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.basedata.category-has-children", childCount);
        }
        // 被物料引用禁删（DATA_IN_USE 是需求约定的删除保护）
        Long refCount = materialMapper.selectCount(Wrappers.lambdaQuery(BaseMaterial.class)
                .eq(BaseMaterial::getCategoryId, id));
        if (refCount != null && refCount > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.basedata.category-in-use", refCount);
        }
        categoryMapper.deleteById(id);
    }

    @Override
    public Set<Long> collectSelfAndDescendantIds(Long categoryId) {
        Set<Long> result = new HashSet<>();
        if (categoryId == null) {
            return result;
        }
        Map<Long, List<BaseMaterialCategory>> byParent = categoryMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(BaseMaterialCategory::getParentId));
        collect(categoryId, byParent, result);
        return result;
    }

    /** BFS/DFS 收集自身与全部子孙（HashSet 天然防环重复） */
    private void collect(Long id, Map<Long, List<BaseMaterialCategory>> byParent, Set<Long> out) {
        if (!out.add(id)) {
            return;
        }
        for (BaseMaterialCategory child : byParent.getOrDefault(id, List.of())) {
            collect(child.getId(), byParent, out);
        }
    }

    /** 表单字段拷贝 */
    private void applyForm(BaseMaterialCategory entity, BaseMaterialCategoryForm form) {
        entity.setParentId(form.getParentId());
        entity.setCode(form.getCode().trim());
        entity.setName(form.getName().trim());
        entity.setSort(form.getSort());
        entity.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        entity.setRemark(form.getRemark());
    }

    /** 分类编码唯一性校验（排除自身） */
    private void checkCodeUnique(String code, Long excludeId) {
        Long count = categoryMapper.selectCount(Wrappers.lambdaQuery(BaseMaterialCategory.class)
                .eq(BaseMaterialCategory::getCode, code)
                .ne(excludeId != null, BaseMaterialCategory::getId, excludeId));
        if (count != null && count > 0) {
            throw BusinessException.ofKey(ErrorCode.DUPLICATE_DATA, "erp.basedata.category-code-exists2", code);
        }
    }

    /**
     * 父分类校验：存在性 + 禁止把分类挂到自己或自己的子孙下（防树成环）。
     */
    private void checkParent(Long parentId, Long selfId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.basedata.parent-category-self");
        }
        BaseMaterialCategory parent = categoryMapper.selectById(parentId);
        if (parent == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.parent-category-not-exists", parentId);
        }
        if (selfId != null && collectSelfAndDescendantIds(selfId).contains(parentId)) {
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.basedata.parent-category-descendant");
        }
    }
}
