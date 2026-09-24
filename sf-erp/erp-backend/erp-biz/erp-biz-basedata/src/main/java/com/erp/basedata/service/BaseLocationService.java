package com.erp.basedata.service;

import com.erp.basedata.dto.form.BaseLocationForm;
import com.erp.basedata.dto.vo.BaseLocationVO;

import java.util.List;

/**
 * 库位 Service（仓库下多库位）。
 * <p>对外能力：listByWarehouse 供库存模块按仓库带出库位。
 */
public interface BaseLocationService {

    /**
     * 查询指定仓库下全部库位（含停用，便于前端标记）。
     *
     * @param warehouseId 仓库 ID
     * @return 库位 VO 列表
     */
    List<BaseLocationVO> listByWarehouse(Long warehouseId);

    /**
     * 新增库位（编码在仓库内唯一）。
     *
     * @param form 库位表单
     * @return 新增后的 ID
     */
    Long create(BaseLocationForm form);

    /**
     * 修改库位（不允许变更所属仓库）。
     *
     * @param id   库位 ID
     * @param form 库位表单
     */
    void update(Long id, BaseLocationForm form);

    /**
     * 删除库位（逻辑删除）。
     * <p>库存模块上线后需补充库存占用校验（有库存抛 DATA_IN_USE）。
     *
     * @param id 库位 ID
     */
    void delete(Long id);
}
