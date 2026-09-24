package com.erp.qc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.qc.entity.QcInspectItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 检验单明细 Mapper。
 */
public interface QcInspectItemMapper extends BaseMapper<QcInspectItem> {

    /**
     * 按检验单 ID查明细行（按 seq、id 排序）。
     */
    @Select("SELECT * FROM qc_inspect_item WHERE inspect_id = #{inspectId} AND deleted = 0 "
            + "ORDER BY seq ASC, id ASC")
    List<QcInspectItem> selectByInspectId(@Param("inspectId") Long inspectId);
}
