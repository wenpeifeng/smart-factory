package com.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.inventory.dto.request.MovementQuery;
import com.erp.inventory.dto.vo.MovementVO;
import com.erp.inventory.dto.vo.TraceNodeVO;
import com.erp.inventory.entity.InvMovement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存流水 Mapper。
 * <p>
 * 二期新增批次追溯查询（正溯/反溯）：按 materialId+batchNo 过滤 inv_movement，
 * 只读流水本身（biz_type/biz_no），不 JOIN 采购/销售等业务实体。
 */
@Mapper
public interface InvMovementMapper extends BaseMapper<InvMovement> {

    /**
     * 流水分页查询（日期/类型/物料/仓库/单号过滤，LEFT JOIN 基础资料补名称）。
     */
    IPage<MovementVO> selectMovementPage(IPage<MovementVO> page, @Param("q") MovementQuery query);

    /**
     * 批次反溯：该批次的全部入库流水（qty &gt; 0，批次从哪来——入库单/采购单号）。
     *
     * @return 按时间正序（先入先展示）
     */
    List<TraceNodeVO> selectBackwardTrace(@Param("materialId") Long materialId,
                                          @Param("batchNo") String batchNo);

    /**
     * 批次正溯：该批次的全部出库流水（qty &lt; 0，批次到哪去——出库单/调拨/领用单号）。
     *
     * @return 按时间正序（先出先展示）
     */
    List<TraceNodeVO> selectForwardTrace(@Param("materialId") Long materialId,
                                         @Param("batchNo") String batchNo);
}
