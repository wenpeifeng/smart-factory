package com.erp.purchase.service;

import com.erp.common.result.PageResult;
import com.erp.purchase.dto.form.PurReturnForm;
import com.erp.purchase.dto.query.PurReturnQuery;
import com.erp.purchase.dto.vo.PurReturnVO;
import com.erp.purchase.dto.vo.ReturnPreviewVO;

/**
 * 采购退货单 Service。
 * <p>
 * 引用已审核采购入库单退货，状态机：0草稿 -> 10已审核。
 * 审核在同一事务内完成：反向库存流水（负数量）-> 条件 UPDATE 扣库存（禁止负库存）
 * -> 回写入库明细已退数量与订单累计入库数量。
 */
public interface PurReturnService {

    /**
     * 分页查询退货单（退货单号/入库单号模糊、供应商、状态、日期区间）。
     */
    PageResult<PurReturnVO> page(PurReturnQuery query);

    /**
     * 退货单详情（主表 + 明细）。
     *
     * @throws com.erp.common.exception.BusinessException 单据不存在
     */
    PurReturnVO detail(Long id);

    /**
     * 选单预填充：按已审核入库单生成退货表单数据
     * （建议退货数量 = 入库数量 - 累计已退，已过滤无可退数量的行）。
     *
     * @param instockId 入库单 ID
     */
    ReturnPreviewVO previewFromInstock(Long instockId);

    /**
     * 新增退货单（草稿，退货数量禁止超过可退数量）。
     *
     * @return 新退货单 ID
     */
    Long create(PurReturnForm form);

    /**
     * 修改退货单（仅草稿）。
     */
    void update(Long id, PurReturnForm form);

    /**
     * 审核退货单（事务：反向库存流水 + 扣库存 + 回写入库明细/订单，任一失败整体回滚）。
     *
     * @param id 退货单 ID
     */
    void audit(Long id);

    /**
     * 删除退货单（仅草稿，逻辑删除）。
     */
    void delete(Long id);
}
