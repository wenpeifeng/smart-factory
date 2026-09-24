package com.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.inventory.dto.request.WmsTaskQuery;
import com.erp.inventory.dto.vo.OutboundDocVO;
import com.erp.inventory.dto.vo.WmsTaskVO;
import com.erp.inventory.entity.WmsTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * WMS 作业任务 Mapper（V421）。
 * <p>
 * 扫描器候选 SQL 只读采购/销售/生产约定表（pur_instock/sal_return/sal_outstock/mrp_work_order），
 * 不 import 对方代码、不做物理外键——与 inv_stock LEFT JOIN base_* 的跨模块约定一致。
 */
@Mapper
public interface WmsTaskMapper extends BaseMapper<WmsTask> {

    /**
     * 任务分页（JOIN 基础资料补编码名称 + 库位编码 + 波次号）。
     */
    IPage<WmsTaskVO> selectTaskPage(IPage<WmsTaskVO> page, @Param("q") WmsTaskQuery query);

    /**
     * 波次任务列表（拣选路径序 = 按库位编码排序；库位为空的排最后）。
     *
     * @param waveId 波次 ID
     */
    List<WmsTaskVO> selectWaveTasks(@Param("waveId") Long waveId);

    /**
     * 任务看板：按 (task_type, status) 分组计数。
     *
     * @param warehouseId 仓库 ID（null=全仓）
     * @return [{task_type, status, cnt}]
     */
    List<Map<String, Object>> selectBoard(@Param("warehouseId") Long warehouseId);

    /**
     * 扫描器候选：已审核但未生成上架任务的入库单行（按单据+物料+批次聚合）。
     * <p>
     * 来源（全部只读）：pur_instock(status=10)+明细、sal_return(status=10)+明细、
     * inv_other_in(status=1)+明细、mrp_work_order(FINISHED 且有入库仓)。
     * 去重：NOT EXISTS 同 (source_type, source_id, material_id, batch_no) 的未取消任务。
     *
     * @param daysBack 只扫近 N 天审核/创建的单据（防止存量单据一次性涌入）
     */
    List<Map<String, Object>> selectPutawayCandidates(@Param("daysBack") int daysBack);

    /**
     * 扫描器候选：已审核但未生成拣选任务的出库单行。
     * 来源（只读）：sal_outstock(status=10)+明细、inv_other_out(status=1)+明细。
     */
    List<Map<String, Object>> selectPickCandidates(@Param("daysBack") int daysBack);

    /**
     * 建波可选的出库单清单：已审核且未全部生成拣选任务的出库单（含行数与总量）。
     */
    List<OutboundDocVO> selectOutboundDocsForWave(@Param("warehouseId") Long warehouseId,
                                                  @Param("daysBack") int daysBack);

    /**
     * 推荐上架库位：同物料在该仓已有库存的库位（优先沿用，避免同料分散）。
     * <p>
     * 只查库位行（location_id>0）；排除 DEFECT 不良品库位。
     */
    Map<String, Object> selectExistingLocation(@Param("warehouseId") Long warehouseId,
                                               @Param("materialId") Long materialId);

    /**
     * 推荐上架库位：STORAGE 存储区/存储用途的空容库位（容量=0 视为不限）。
     * 占用量 = 该库位全部库存行之和；按库位编码升序取第一个。
     */
    Map<String, Object> selectFreeStorageLocation(@Param("warehouseId") Long warehouseId);

    /**
     * 推荐拣选来源库位：该物料在该仓有余量的库位行（余量最大优先，其次编码序）。
     * 返回行含 batch_no/location_id/qty，任务据此锁定批次与库位。
     */
    Map<String, Object> selectPickSourceRow(@Param("warehouseId") Long warehouseId,
                                            @Param("materialId") Long materialId);

    /**
     * KPI：窗口内完成任务统计（完成数/平均耗时分钟/当日完成数）。
     *
     * @param beginTime 窗口起（含）
     * @param endTime   窗口止（不含）
     * @return {completed, avg_minutes, on_time}
     */
    Map<String, Object> selectKpiDone(@Param("beginTime") java.time.LocalDateTime beginTime,
                                       @Param("endTime") java.time.LocalDateTime endTime);

    /**
     * KPI：按执行人统计（完成数/平均耗时分钟）。
     *
     * @return 人效行集
     */
    List<com.erp.inventory.dto.vo.WmsKpiVO.AssigneeKpi> selectKpiByAssignee(
            @Param("beginTime") java.time.LocalDateTime beginTime,
            @Param("endTime") java.time.LocalDateTime endTime);
}
