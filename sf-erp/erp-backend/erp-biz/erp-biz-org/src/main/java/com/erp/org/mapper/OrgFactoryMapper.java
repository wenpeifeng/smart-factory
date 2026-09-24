package com.erp.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.org.dto.request.FactoryQuery;
import com.erp.org.dto.raw.WarehouseRaw;
import com.erp.org.dto.vo.FactoryStockVO;
import com.erp.org.dto.vo.FactoryVO;
import com.erp.org.dto.vo.WarehouseBelongVO;
import com.erp.org.entity.OrgFactory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工厂档案 Mapper。
 * <p>
 * 跨模块约定：base_warehouse / inv_stock 为 basedata / inventory 模块标准表，
 * 此处仅按约定列只读 JOIN（仓库清单/库存统计）与 factory_id 归属列回写，
 * 不 import 对方代码、不做物理外键。SQL 见 resources/mapper/org/OrgFactoryMapper.xml。
 */
public interface OrgFactoryMapper extends BaseMapper<OrgFactory> {

    /**
     * 工厂分页（名称/编码模糊 + 状态），带归属仓库数。
     */
    IPage<FactoryVO> pageFactory(IPage<FactoryVO> page, @Param("q") FactoryQuery query);

    /**
     * 工厂下仓库清单（含归属工厂名回显，只读 JOIN base_warehouse）。
     */
    List<WarehouseBelongVO> selectFactoryWarehouses(@Param("factoryId") Long factoryId);

    /**
     * 取仓库归属信息（id/code/name/factory_id/status），不存在或已删返回 null。
     */
    WarehouseRaw selectWarehouse(@Param("warehouseId") Long warehouseId);

    /**
     * 仓库归属调整：回写 base_warehouse.factory_id（只动这一列）。
     *
     * @return 影响行数（0=仓库不存在或已删）
     */
    int updateWarehouseFactory(@Param("warehouseId") Long warehouseId, @Param("factoryId") Long factoryId);

    /**
     * 统计工厂下仓库数（删除工厂前校验用）。
     */
    long countFactoryWarehouses(@Param("factoryId") Long factoryId);

    /**
     * 工厂下各仓库库存量汇总（JOIN inv_stock × base_warehouse 只读聚合，含零库存仓库）。
     */
    List<FactoryStockVO> statisticsStock(@Param("factoryId") Long factoryId);
}
