package com.erp.purchase.service;

import com.erp.common.result.PageResult;
import com.erp.purchase.dto.form.PurInstockForm;
import com.erp.purchase.dto.query.PurInstockQuery;
import com.erp.purchase.dto.vo.InstockPreviewVO;
import com.erp.purchase.dto.vo.PurInstockVO;

/**
 * 采购入库单 Service。
 * <p>
 * 从已审核采购订单生成（选单），状态机：0草稿 -> 10已审核。
 * 审核在同一事务内完成：写库存流水 -> 增库存 -> 生成应付 -> 回写订单累计入库与进度状态。
 */
public interface PurInstockService {

    /**
     * 分页查询入库单（单号/订单号模糊、供应商、状态、日期区间）。
     */
    PageResult<PurInstockVO> page(PurInstockQuery query);

    /**
     * 入库单详情（主表 + 明细）。
     *
     * @throws com.erp.common.exception.BusinessException 单据不存在
     */
    PurInstockVO detail(Long id);

    /**
     * 选单预填充：按已审核/部分入库的采购订单生成入库表单数据
     * （建议实收数量 = 订单数量 - 累计已入库，已过滤无可入库数量的行）。
     *
     * @param orderId 采购订单 ID
     */
    InstockPreviewVO previewFromOrder(Long orderId);

    /**
     * 新增入库单（草稿，实收数量禁止超收）。
     *
     * @return 新入库单 ID
     */
    Long create(PurInstockForm form);

    /**
     * 修改入库单（仅草稿）。
     */
    void update(Long id, PurInstockForm form);

    /**
     * 审核入库单（事务：库存流水 + 库存 + 应付 + 订单回写，任一失败整体回滚）。
     *
     * @param id 入库单 ID
     */
    void audit(Long id);

    /**
     * 删除入库单（仅草稿，逻辑删除）。
     */
    void delete(Long id);
}
