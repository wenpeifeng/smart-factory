package com.erp.inventory.service;

import com.erp.common.result.PageResult;
import com.erp.inventory.dto.request.WmsTaskQuery;
import com.erp.inventory.dto.vo.AbcClassVO;

import java.util.List;
import java.util.Map;

/**
 * WMS ABC 分级 Service（V421）—— 物料 ABC 分级 + 按分级频度自动生成循环盘点单。
 */
public interface WmsAbcService {

    /**
     * 现行分级分页（按出库频次降序）。
     */
    PageResult<AbcClassVO> page(WmsTaskQuery query);

    /**
     * 手动重算分级：近 90 天出库频次降序排名，前 20% A / 随后 30% B / 其余 50% C；
     * 旧记录软删、新记录全量写入（同事务）。无出库记录时分级表清空（全部软删）。
     *
     * @return 各级物料数 [{level, cnt}]
     */
    List<Map<String, Object>> recalc();

    /**
     * 各分级物料数（KPI 卡片）。
     */
    List<Map<String, Object>> levelCounts();

    /**
     * 月度循环盘点（定时触发，每月 1 号 02:00）：
     * 按仓库现存的最高优先级分级决定盘点间隔（A=1 月 / B=3 月 / C=12 月），
     * 该仓距上次 CYCLE 盘点超过间隔则创建盘点单（source=CYCLE）。
     *
     * @return 本次生成的盘点单数
     */
    int generateCycleChecks();
}
