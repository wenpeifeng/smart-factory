package com.erp.inventory.service;

import com.erp.common.result.PageResult;
import com.erp.inventory.dto.form.CheckActualForm;
import com.erp.inventory.dto.form.CheckCreateForm;
import com.erp.inventory.dto.request.CheckQuery;
import com.erp.inventory.dto.vo.CheckDocDetailVO;
import com.erp.inventory.dto.vo.CheckVO;

/**
 * 库存盘点服务：生成快照 → 录实盘 → 差异调平。
 */
public interface InvCheckService {

    /**
     * 创建盘点单：选仓库，把该仓库当前 inv_stock 全量抄入明细（快照）。
     *
     * @param form 创建表单（仓库 + 备注）
     * @return 新盘点单 ID
     */
    Long create(CheckCreateForm form);

    /**
     * 创建盘点单（带来源，V421）：ABC 循环盘点定时任务以 source=CYCLE 调用，
     * 页面列表可区分手工/自动盘点。
     *
     * @param warehouseId 盘点仓库
     * @param source      来源（MANUAL/CYCLE）
     * @param remark      备注
     * @return 新盘点单 ID
     */
    Long createWithSource(Long warehouseId, String source, String remark);

    /**
     * 录入/保存实盘数量（可分批多次保存）：写 actual_qty 并计算 diff_qty=实盘-账面。
     *
     * @param id   盘点单 ID
     * @param form 实盘行（明细 ID + 实盘数量）
     * @throws com.erp.common.exception.BusinessException 盘点单不存在 / 已完成 / 明细行不属于该单
     */
    void saveActual(Long id, CheckActualForm form);

    /**
     * 完成盘点：对已录实盘且差异不为 0 的行生成 ADJUST 流水调平库存
     * （盘盈入库/盘亏出库），随后单据置为已完成。未录实盘的行不调平。
     *
     * @param id 盘点单 ID
     * @return 调平的行数（差异为 0 或未盘点的行不计）
     * @throws com.erp.common.exception.BusinessException 盘点单不存在 / 已完成
     */
    int complete(Long id);

    /**
     * 删除盘点单（仅盘点中可删；已完成说明库存已调平，不允许删）。
     *
     * @param id 盘点单 ID
     */
    void delete(Long id);

    /**
     * 分页查询。
     */
    PageResult<CheckVO> page(CheckQuery query);

    /**
     * 盘点单详情（头 + 明细行）。
     *
     * @param id 盘点单 ID
     */
    CheckDocDetailVO detail(Long id);
}
