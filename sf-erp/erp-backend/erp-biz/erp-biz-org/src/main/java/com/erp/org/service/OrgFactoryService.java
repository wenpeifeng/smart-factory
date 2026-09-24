package com.erp.org.service;

import com.erp.common.result.PageResult;
import com.erp.org.dto.form.AssignWarehouseForm;
import com.erp.org.dto.form.FactoryForm;
import com.erp.org.dto.request.FactoryQuery;
import com.erp.org.dto.vo.FactoryStockVO;
import com.erp.org.dto.vo.FactoryVO;
import com.erp.org.dto.vo.WarehouseBelongVO;

import java.util.List;

/**
 * 工厂档案服务：CRUD / 启停 / 仓库归属管理 / 工厂库存统计。
 */
public interface OrgFactoryService {

    /**
     * 工厂分页（名称/编码模糊 + 状态，带归属仓库数）。
     */
    PageResult<FactoryVO> page(FactoryQuery query);

    /**
     * 启用工厂清单（下拉选择器用）。
     */
    List<FactoryVO> listEnabled();

    /**
     * 工厂详情。
     *
     * @throws com.erp.common.exception.BusinessException 工厂不存在
     */
    FactoryVO getDetail(Long id);

    /**
     * 新增工厂（编码留空自动生成 FC+yyyyMMdd+3位流水）。
     *
     * @return 工厂 ID
     */
    Long create(FactoryForm form);

    /**
     * 修改工厂（编码不可改，保持唯一身份稳定）。
     *
     * @throws com.erp.common.exception.BusinessException 工厂不存在
     */
    void update(Long id, FactoryForm form);

    /**
     * 删除工厂（逻辑删除；总厂与仍有仓库归属的工厂不可删）。
     *
     * @throws com.erp.common.exception.BusinessException 不可删除场景
     */
    void delete(Long id);

    /**
     * 启用/停用工厂（停用后不可作为调拨/归属目标，存量归属保留）。
     */
    void updateStatus(Long id, Integer status);

    /**
     * 工厂下仓库清单（JOIN base_warehouse 只读）。
     *
     * @throws com.erp.common.exception.BusinessException 工厂不存在
     */
    List<WarehouseBelongVO> listWarehouses(Long factoryId);

    /**
     * 仓库归属调整：回写 base_warehouse.factory_id。
     *
     * @throws com.erp.common.exception.BusinessException 仓库不存在 / 工厂不存在或停用
     */
    void assignWarehouse(AssignWarehouseForm form);

    /**
     * 工厂下各仓库库存量汇总（JOIN inv_stock × base_warehouse 只读聚合）。
     *
     * @throws com.erp.common.exception.BusinessException 工厂不存在
     */
    List<FactoryStockVO> statisticsStock(Long factoryId);
}
