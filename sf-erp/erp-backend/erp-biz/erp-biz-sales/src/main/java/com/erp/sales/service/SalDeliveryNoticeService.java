package com.erp.sales.service;

import com.erp.common.result.PageResult;
import com.erp.sales.dto.form.NoticeSettleForm;
import com.erp.sales.dto.form.SalDeliveryNoticeForm;
import com.erp.sales.dto.vo.PendingStockVO;
import com.erp.sales.dto.vo.SalNoticeDetailBundle;
import com.erp.sales.dto.vo.SalNoticeVO;

import java.util.List;

/**
 * 销售发货通知单 Service。
 * <p>
 * 状态机：0草稿 →(通知)→ 10已通知 →(全部明细核销)→ 20已出库（终态）；
 * 0/10 且未发生出库 →(取消)→ 30已取消（终态）。
 * <p>
 * 已出库数量回写挂点约定（不修改既有出库代码）：
 * 出库单创建时 remark 记 FH 通知单号；出库审核后调
 * {@link #settle(Long, NoticeSettleForm)} 按出库单号核销（守卫不超通知量），
 * 主线程后续可改为出库审核时自动核销。
 */
public interface SalDeliveryNoticeService {

    /**
     * 分页查询发货通知单。
     *
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @param keyword  关键词（通知单号/来源订单号/客户名称，可空）
     * @param status   状态（可空）
     * @return 通知单分页
     */
    PageResult<SalNoticeVO> page(long pageNum, long pageSize, String keyword, Integer status);

    /**
     * 查询通知单详情（表头 + 明细行，含已出库/剩余数量）。
     *
     * @param id 通知单 ID
     * @return 详情聚合 VO
     * @throws com.erp.common.exception.BusinessException 通知单不存在
     */
    SalNoticeDetailBundle getDetail(Long id);

    /**
     * 新增通知单（草稿态）：从已审核订单选单生成（守卫订单行余量）
     * 或无来源直发（客户/仓库/物料手工指定）。
     *
     * @param form 通知单表单
     * @return 新通知单 ID
     * @throws com.erp.common.exception.BusinessException 来源订单非法 / 超余量 / 主数据不存在
     */
    Long create(SalDeliveryNoticeForm form);

    /**
     * 修改通知单（仅草稿，明细整体替换）。
     *
     * @param id   通知单 ID
     * @param form 通知单表单
     * @throws com.erp.common.exception.BusinessException 不存在或非草稿态
     */
    void update(Long id, SalDeliveryNoticeForm form);

    /**
     * 删除通知单（仅草稿，逻辑删除，含明细行）。
     *
     * @param id 通知单 ID
     * @throws com.erp.common.exception.BusinessException 不存在或非草稿态
     */
    void delete(Long id);

    /**
     * 通知发货：0草稿 → 10已通知。
     *
     * @param id 通知单 ID
     * @throws com.erp.common.exception.BusinessException 非草稿态或并发冲突
     */
    void notifyShip(Long id);

    /**
     * 取消通知：0/10 且未发生任何出库 → 30已取消。
     *
     * @param id 通知单 ID
     * @throws com.erp.common.exception.BusinessException 已发生出库或状态不允许
     */
    void cancel(Long id);

    /**
     * 按出库单号核销出库数量：读取已审核出库单明细，按物料（有订单行引用时
     * 按订单明细精确匹配）累加到通知行，守卫不超通知数量；全部核销到位后
     * 通知单流转为 20已出库。
     *
     * @param id   通知单 ID
     * @param form 出库单号表单
     * @throws com.erp.common.exception.BusinessException 出库单不存在/未审核、
     *         物料不匹配、超出通知剩余数量
     */
    void settle(Long id, NoticeSettleForm form);

    /**
     * 备货参考：物料 × 仓库维度可用量（SUM inv_stock.qty）。
     *
     * @param warehouseId 仓库 ID（可空 = 全部仓库）
     * @param keyword     物料编码/名称模糊（可空）
     * @return 备货参考行列表
     */
    List<PendingStockVO> pendingStocks(Long warehouseId, String keyword);
}
