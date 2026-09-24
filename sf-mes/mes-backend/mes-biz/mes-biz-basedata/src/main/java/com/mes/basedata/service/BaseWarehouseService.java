package com.mes.basedata.service;

import com.mes.basedata.dto.form.BaseWarehouseForm;
import com.mes.basedata.dto.vo.BaseWarehouseVO;
import com.mes.basedata.entity.BaseWarehouse;
import com.mes.common.result.PageResult;

import java.util.Collection;
import java.util.List;

/**
 * 仓库 Service。
 * <p>对外能力：getByIds / listEnabled 供库存、采购入库、销售出库模块使用。
 */
public interface BaseWarehouseService {

    /**
     * 分页查询仓库。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  关键词，模糊匹配编码/名称（可空）
     * @return 分页结果
     */
    PageResult<BaseWarehouseVO> page(long pageNum, long pageSize, String keyword);

    /**
     * 按 ID 查详情。
     *
     * @param id 仓库 ID
     * @return 仓库 VO
     * @throws com.mes.common.exception.BusinessException DATA_NOT_EXISTS 若不存在
     */
    BaseWarehouseVO getById(Long id);

    /**
     * 新增仓库（编码留空时自动生成 CK+日期+3位流水）。
     *
     * @param form 仓库表单
     * @return 新增后的 ID
     */
    Long create(BaseWarehouseForm form);

    /**
     * 修改仓库。
     *
     * @param id   仓库 ID
     * @param form 仓库表单
     */
    void update(Long id, BaseWarehouseForm form);

    /**
     * 删除仓库（逻辑删除）。
     *
     * @param id 仓库 ID
     * @throws com.mes.common.exception.BusinessException DATA_IN_USE 存在库位时禁止删除
     */
    void delete(Long id);

    /**
     * 批量按 ID 查实体（跨模块用）。
     *
     * @param ids 仓库 ID 集合
     * @return 仓库实体列表
     */
    List<BaseWarehouse> getByIds(Collection<Long> ids);

    /**
     * 启用仓库列表（入库/出库/调拨选择器用）。
     *
     * @return 启用仓库 VO 列表
     */
    List<BaseWarehouseVO> listEnabled();
}
