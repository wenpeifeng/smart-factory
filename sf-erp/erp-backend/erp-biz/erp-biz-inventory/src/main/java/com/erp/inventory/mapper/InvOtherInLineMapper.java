package com.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.inventory.dto.vo.OtherDocLineVO;
import com.erp.inventory.entity.InvOtherInLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 其他入库单明细行 Mapper。
 */
@Mapper
public interface InvOtherInLineMapper extends BaseMapper<InvOtherInLine> {

    /**
     * 按头表 ID 查明细行（LEFT JOIN 物料取编码/名称）。
     */
    List<OtherDocLineVO> selectLinesByDocId(@Param("docId") Long docId);
}
