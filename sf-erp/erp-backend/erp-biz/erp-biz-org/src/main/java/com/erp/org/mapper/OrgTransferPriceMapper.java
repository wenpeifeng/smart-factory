package com.erp.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.org.dto.request.TransferPriceQuery;
import com.erp.org.dto.vo.TransferPriceVO;
import com.erp.org.entity.OrgTransferPrice;
import org.apache.ibatis.annotations.Param;

/**
 * 组织间调拨价目 Mapper。SQL 见 resources/mapper/org/OrgTransferPriceMapper.xml。
 */
public interface OrgTransferPriceMapper extends BaseMapper<OrgTransferPrice> {

    /**
     * 价目分页（物料/调出工厂/调入工厂/状态，带名称回显）。
     */
    IPage<TransferPriceVO> pagePrice(IPage<TransferPriceVO> page, @Param("q") TransferPriceQuery query);

    /**
     * 取线路现行价目（物料×调出×调入，启用状态优先最新，无启用价目返回 null）。
     */
    OrgTransferPrice selectEffectiveRule(@Param("materialId") Long materialId,
                                         @Param("fromFactoryId") Long fromFactoryId,
                                         @Param("toFactoryId") Long toFactoryId);
}
