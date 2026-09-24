package com.mes.basedata.service;

import com.mes.basedata.dto.form.BaseUnitForm;
import com.mes.basedata.dto.vo.BaseUnitVO;
import com.mes.basedata.entity.BaseUnit;
import com.mes.common.result.PageResult;

import java.util.List;

/**
 * 计量单位 Service。
 * <p>供物料管理与其他模块选择单位、做组内换算（换算公式见 {@link BaseUnit}）。
 */
public interface BaseUnitService {

    /**
     * 分页查询计量单位。
     *
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @param keyword  关键词，模糊匹配单位名称或换算组编码（可空）
     * @return 分页结果
     */
    PageResult<BaseUnitVO> page(long pageNum, long pageSize, String keyword);

    /**
     * 全部启用中的计量单位（下拉选择用，不分页）。
     *
     * @return 启用单位列表
     */
    List<BaseUnitVO> listEnabled();

    /**
     * 按 ID 查详情。
     *
     * @param id 单位 ID
     * @return 单位 VO
     * @throws com.mes.common.exception.BusinessException DATA_NOT_EXISTS 若不存在
     */
    BaseUnitVO getById(Long id);

    /**
     * 新增计量单位。
     *
     * @param form 单位表单
     * @return 新增后的 ID
     */
    Long create(BaseUnitForm form);

    /**
     * 修改计量单位。
     *
     * @param id   单位 ID
     * @param form 单位表单
     */
    void update(Long id, BaseUnitForm form);

    /**
     * 删除计量单位（逻辑删除）。
     *
     * @param id 单位 ID
     * @throws com.mes.common.exception.BusinessException DATA_IN_USE 若被物料引用
     */
    void delete(Long id);
}
