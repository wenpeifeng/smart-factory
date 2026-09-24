package com.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.inventory.dto.request.WmsTaskQuery;
import com.erp.inventory.dto.vo.WmsWaveVO;
import com.erp.inventory.entity.WmsWave;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * WMS 波次 Mapper（V421）——分页列表（含波内任务进度统计）。
 */
@Mapper
public interface WmsWaveMapper extends BaseMapper<WmsWave> {

    /**
     * 波次分页：统计波内任务总数/已完成数/总数量。
     */
    IPage<WmsWaveVO> selectWavePage(IPage<WmsWaveVO> page, @Param("q") WmsTaskQuery query);
}
