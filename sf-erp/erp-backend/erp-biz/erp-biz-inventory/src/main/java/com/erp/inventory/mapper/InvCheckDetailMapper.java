package com.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.inventory.dto.vo.CheckDetailVO;
import com.erp.inventory.entity.InvCheckDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存盘点明细 Mapper。
 */
@Mapper
public interface InvCheckDetailMapper extends BaseMapper<InvCheckDetail> {

    /**
     * 生成盘点快照：把该仓库当前 inv_stock 全量抄入盘点明细
     * （book_qty=当前库存，actual_qty/diff_qty 留空）。
     * INSERT...SELECT 原子完成，避免应用层逐行搬运。
     *
     * @return 抄入行数（即该仓库当前的库存记录数）
     */
    int snapshotFromStock(@Param("checkId") Long checkId, @Param("warehouseId") Long warehouseId);

    /**
     * 按盘点单查明细行（LEFT JOIN 物料取编码/名称）。
     */
    List<CheckDetailVO> selectDetailsByCheckId(@Param("checkId") Long checkId);
}
