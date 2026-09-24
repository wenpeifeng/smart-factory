package com.mes.basedata.service;

import com.mes.basedata.dto.form.BaseMaterialCategoryForm;
import com.mes.basedata.dto.vo.BaseMaterialCategoryVO;
import com.mes.basedata.entity.BaseMaterialCategory;

import java.util.List;
import java.util.Set;

/**
 * 物料分类 Service（树形）。
 * <p>对外能力：分类树查询、按分类聚合子孙 ID（供物料按分类筛选）。
 */
public interface BaseMaterialCategoryService {

    /**
     * 查询完整分类树（含停用，前端树高亮区分）。
     *
     * @return 根节点列表（parentId=0）
     */
    List<BaseMaterialCategoryVO> tree();

    /**
     * 按 ID 查详情。
     *
     * @param id 分类 ID
     * @return 分类实体
     * @throws com.mes.common.exception.BusinessException DATA_NOT_EXISTS 若不存在
     */
    BaseMaterialCategory getById(Long id);

    /**
     * 新增分类。
     *
     * @param form 分类表单
     * @return 新增后的 ID
     */
    Long create(BaseMaterialCategoryForm form);

    /**
     * 修改分类。
     *
     * @param id   分类 ID
     * @param form 分类表单
     */
    void update(Long id, BaseMaterialCategoryForm form);

    /**
     * 删除分类（逻辑删除）。
     *
     * @param id 分类 ID
     * @throws com.mes.common.exception.BusinessException DATA_IN_USE 存在子分类或被物料引用时
     */
    void delete(Long id);

    /**
     * 收集指定分类自身及全部子孙分类 ID。
     * 供物料分页按分类（含子孙）筛选。
     *
     * @param categoryId 分类 ID
     * @return ID 集合（分类不存在时返回空集合）
     */
    Set<Long> collectSelfAndDescendantIds(Long categoryId);
}
