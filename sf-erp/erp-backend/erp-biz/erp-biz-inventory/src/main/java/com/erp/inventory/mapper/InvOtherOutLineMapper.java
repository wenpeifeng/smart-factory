package com.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.inventory.dto.vo.OtherDocLineVO;
import com.erp.inventory.entity.InvOtherOutLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 其他出库单明细行 Mapper。
 */
@Mapper
public interface InvOtherOutLineMapper extends BaseMapper<InvOtherOutLine> {

    /**
     * 按头表 ID 查明细行（LEFT JOIN 物料取编码/名称）。
     */
    List<OtherDocLineVO> selectLinesByDocId(@Param("docId") Long docId);
}
