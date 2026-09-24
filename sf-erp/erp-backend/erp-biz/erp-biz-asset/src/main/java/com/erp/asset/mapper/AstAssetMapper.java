package com.erp.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.asset.entity.AstAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 资产卡片 Mapper。
 * <p>
 * 除 MP 通用 CRUD 外提供 {@link #applyDepreciation}：折旧计提专用条件 UPDATE，
 * 数据库层兜底"仅 在用 且 已提月数 &lt; 预计年限 的资产可计提"，并发下不会多提
 * （SQL 见 resources/mapper/asset/AstAssetMapper.xml）。
 */
@Mapper
public interface AstAssetMapper extends BaseMapper<AstAsset> {

    /**
     * 折旧计提条件累加：累计折旧 +月折旧额、已提月数 +1。
     *
     * @param id     资产 ID
     * @param amount 本月折旧额（&gt;0）
     * @return 影响行数；0 表示资产已不在可计提状态（并发被处置/已提满/删除），调用方必须回滚
     */
    int applyDepreciation(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
