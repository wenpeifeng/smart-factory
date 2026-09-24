package com.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.inventory.dto.request.SerialQuery;
import com.erp.inventory.dto.vo.InvSerialVO;
import com.erp.inventory.entity.InvSerial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 序列号 Mapper（V422）—— 序列号台账分页（正查=序列号精确/前缀、反查=物料维度）。
 */
@Mapper
public interface InvSerialMapper extends BaseMapper<InvSerial> {

    /**
     * 序列号分页：serialNo 精确/前缀匹配 + materialId 反查 + 状态过滤。
     */
    IPage<InvSerialVO> selectSerialPage(IPage<InvSerialVO> page, @Param("q") SerialQuery query);

    /**
     * 按序列号列表批量取在库序列号（出库核验预检）。
     */
    List<InvSerial> selectBySerialNos(@Param("serialNos") List<String> serialNos);
}
