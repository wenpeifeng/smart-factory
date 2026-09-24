package com.erp.inventory.service;

import com.erp.common.result.PageResult;
import com.erp.inventory.dto.form.WaveCreateForm;
import com.erp.inventory.dto.request.WmsTaskQuery;
import com.erp.inventory.dto.vo.OutboundDocVO;
import com.erp.inventory.dto.vo.WmsTaskVO;
import com.erp.inventory.dto.vo.WmsWaveVO;

import java.util.List;

/**
 * WMS 波次 Service（V421）—— 轻量波次管理：多张已审核出库单合并建波 →
 * 拣选路径序执行 → 全部任务完成后关波。
 */
public interface WmsWaveService {

    /**
     * 建波：为所选的每张已审核出库单生成拣选任务（复用扫描器推荐逻辑）并入波。
     * 任一张单生成失败（已生成过/无明细）整体回滚。
     *
     * @return 波次 ID
     */
    Long create(WaveCreateForm form);

    /**
     * 波次分页（含波内任务进度统计）。
     */
    PageResult<WmsWaveVO> page(WmsTaskQuery query);

    /**
     * 波次详情（头信息 + 波内任务，按拣选路径序=库位编码排序）。
     */
    WmsWaveVO detail(Long id);

    /**
     * 拣选路径序：波内任务按库位编码升序返回（库位空的排最后）——最简拣选路径。
     */
    List<WmsTaskVO> pickPath(Long id);

    /**
     * 完成波次：校验波内任务全部完成后置状态=20（拣选中→完成）。
     */
    void complete(Long id);

    /**
     * 建波可选出库单清单（已审核且未全部生成拣选任务）。
     */
    List<OutboundDocVO> outboundDocs(Long warehouseId);
}
