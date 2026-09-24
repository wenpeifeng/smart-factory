package com.erp.basedata.service;

import com.erp.basedata.dto.form.BaseZoneForm;
import com.erp.basedata.dto.vo.BaseZoneVO;
import com.erp.basedata.dto.vo.LocationTreeNodeVO;

import java.util.List;

/**
 * 库区 Service（V421，仓库→库区→库位三级）。
 * <p>对外能力：仓库下库区列表 / 仓库库位树（供 WMS 任务推荐与前端树展示）。
 */
public interface BaseZoneService {

    /**
     * 查询指定仓库下全部库区（按类型+编码排序）。
     *
     * @param warehouseId 仓库 ID
     * @return 库区 VO 列表
     */
    List<BaseZoneVO> listByWarehouse(Long warehouseId);

    /**
     * 仓库库位树：库区分组（未挂库区的库位归"未分区"组）→ 库位按编码升序。
     *
     * @param warehouseId 仓库 ID
     * @return 树节点列表（含未分区兜底组）
     */
    List<LocationTreeNodeVO> locationTree(Long warehouseId);

    /**
     * 新增库区（编码在仓库内唯一）。
     *
     * @param form 库区表单
     * @return 新增后的 ID
     */
    Long create(BaseZoneForm form);

    /**
     * 修改库区（不允许变更所属仓库）。
     *
     * @param id   库区 ID
     * @param form 库区表单
     */
    void update(Long id, BaseZoneForm form);

    /**
     * 删除库区（逻辑删除；库区下仍有库位时拒绝，防库位悬空）。
     *
     * @param id 库区 ID
     */
    void delete(Long id);
}
