package com.erp.asset.service;

import com.erp.asset.dto.form.CategoryForm;
import com.erp.asset.dto.vo.CategoryVO;

import java.util.List;

/**
 * 资产类别服务：折旧政策与科目映射的维护。
 */
public interface AstCategoryService {

    /**
     * 全量类别列表（下拉与维护页共用，按编码排序）。
     *
     * @return 类别 VO 列表
     */
    List<CategoryVO> list();

    /**
     * 新增类别。
     *
     * @param form 类别表单
     * @return 类别 ID
     * @throws com.erp.common.exception.BusinessException 编码重复 / 科目非法
     */
    Long create(CategoryForm form);

    /**
     * 修改类别。
     *
     * @param id   类别 ID
     * @param form 类别表单
     * @throws com.erp.common.exception.BusinessException 类别不存在 / 编码重复 / 科目非法
     */
    void update(Long id, CategoryForm form);

    /**
     * 删除类别（逻辑删除）。
     *
     * @param id 类别 ID
     * @throws com.erp.common.exception.BusinessException 类别不存在 / 仍有资产引用
     */
    void delete(Long id);
}
