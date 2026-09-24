package com.erp.system.service;

import com.erp.common.result.PageResult;
import com.erp.system.dto.dict.DictTypeForm;
import com.erp.system.dto.dict.DictTypeQuery;
import com.erp.system.dto.dict.DictTypeVO;

/**
 * 字典类型管理服务。
 */
public interface SysDictTypeService {

    /**
     * 分页查询字典类型。
     *
     * @param query 查询条件
     * @return 字典类型 VO 分页
     */
    PageResult<DictTypeVO> page(DictTypeQuery query);

    /**
     * 新增字典类型（编码唯一）。
     *
     * @param form 字典类型表单
     * @return 新类型 ID
     */
    Long create(DictTypeForm form);

    /**
     * 修改字典类型（同步级联更新字典数据的 dict_type 编码，保证关联不脱钩）。
     *
     * @param form 字典类型表单（含 id）
     */
    void update(DictTypeForm form);

    /**
     * 删除字典类型（仍存在字典项时拒绝）。
     *
     * @param id 类型 ID
     */
    void delete(Long id);
}
