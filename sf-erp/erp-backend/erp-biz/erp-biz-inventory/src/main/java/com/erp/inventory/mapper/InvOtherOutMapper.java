package com.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.inventory.dto.request.OtherDocQuery;
import com.erp.inventory.dto.vo.OtherDocVO;
import com.erp.inventory.entity.InvOtherOut;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 其他出库单 Mapper。
 */
@Mapper
public interface InvOtherOutMapper extends BaseMapper<InvOtherOut> {

    /**
     * 单据分页（含行数/总数量统计列，LEFT JOIN 仓库取名称）。
     */
    IPage<OtherDocVO> selectDocPage(IPage<OtherDocVO> page, @Param("q") OtherDocQuery query);
}
