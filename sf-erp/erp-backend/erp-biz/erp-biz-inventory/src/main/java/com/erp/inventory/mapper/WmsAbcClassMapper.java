package com.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.inventory.dto.request.WmsTaskQuery;
import com.erp.inventory.dto.vo.AbcClassVO;
import com.erp.inventory.entity.WmsAbcClass;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * WMS ABC 分级 Mapper（V421）—— 现行分级分页 + 近 90 天出库频次统计。
 */
@Mapper
public interface WmsAbcClassMapper extends BaseMapper<WmsAbcClass> {

    /**
     * 现行分级分页（含物料编码名称）。
     */
    IPage<AbcClassVO> selectAbcPage(IPage<AbcClassVO> page, @Param("q") WmsTaskQuery query);

    /**
     * 近 90 天各物料出库频次（inv_movement 出库流水行数，降序）——分级原始数据。
     */
    List<Map<String, Object>> selectOutboundFrequency(@Param("daysBack") int daysBack);

    /**
     * 各分级物料数统计（KPI 卡片/重算结果展示）：[{level, cnt}]。
     */
    List<Map<String, Object>> selectLevelCounts();

    /**
     * 按分级取物料仓库集合：循环盘点到期判断用——返回分级涉及的仓库（有库存行的仓库）。
     */
    List<Map<String, Object>> selectWarehousesWithClassLevel(@Param("level") String level);
}
