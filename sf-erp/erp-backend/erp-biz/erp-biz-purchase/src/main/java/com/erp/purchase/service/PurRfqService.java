package com.erp.purchase.service;

import com.erp.common.result.PageResult;
import com.erp.purchase.dto.form.PurRfqForm;
import com.erp.purchase.dto.form.RfqAwardForm;
import com.erp.purchase.dto.form.RfqQuoteForm;
import com.erp.purchase.dto.query.PurRfqQuery;
import com.erp.purchase.dto.vo.PurRfqVO;
import com.erp.purchase.dto.vo.RfqComparisonVO;
import com.erp.purchase.dto.vo.RfqToOrderVO;

/**
 * 询比价单 Service。
 * <p>
 * 状态机：0询价中 -> 10已定标；0询价中 -> 30已取消（终态）。
 * 已定标禁止修改/删除/再录报价；下推采购订单仅允许一次。
 * <p>
 * 链路：从请购单生成（明细带入 + 超转校验） -> 邀请供应商（>=3 家）
 * -> 按供应商录报价 -> 比价矩阵（物料×供应商，最低价标记）
 * -> 分项定标（回写请购已转数量，条件 UPDATE 兜底防超转）
 * -> 下推采购订单草稿（调既有 {@link PurOrderService#create}，一供应商一单）。
 */
public interface PurRfqService {

    /**
     * 分页查询询比价单（单号模糊/状态/来源请购单/供应商筛选）。
     *
     * @param query 查询条件
     * @return 询比价分页（含来源请购单号，不含供应商/明细）
     */
    PageResult<PurRfqVO> page(PurRfqQuery query);

    /**
     * 询比价单详情（主表 + 邀请供应商 + 询价明细含定标/下推回写）。
     *
     * @param id 询比价单 ID
     * @return 询比价详情
     * @throws com.erp.common.exception.BusinessException 询比价单不存在
     */
    PurRfqVO detail(Long id);

    /**
     * 新增询比价单（询价中）。单号 IN+yyyyMMdd+4 位流水（Redis）自动生成。
     * 关联请购单时校验：请购单须为已提交/已转采购状态，
     * 且每行 询价数量 &lt;= 请购数量 - 已转采购数量。
     *
     * @param form 询比价表单
     * @return 新询比价单 ID
     */
    Long create(PurRfqForm form);

    /**
     * 修改询比价单（仅询价中）：截止日期/备注/邀请供应商可调整；
     * 询价明细创建后不可改（防与已录报价错位），移出邀请列表的供应商其报价一并删除。
     *
     * @param id   询比价单 ID
     * @param form 询比价表单（明细字段忽略）
     */
    void update(Long id, PurRfqForm form);

    /**
     * 录入供应商报价（仅询价中，按供应商整体覆盖式保存）。
     * 同步回写供应商行报价金额合计与"已报价"状态。
     *
     * @param id   询比价单 ID
     * @param form 报价表单
     */
    void saveQuotes(Long id, RfqQuoteForm form);

    /**
     * 比价矩阵：物料 × 供应商（单价/交期/总价），每物料标记最低价供应商（并列都标记）。
     *
     * @param id 询比价单 ID
     * @return 矩阵数据
     */
    RfqComparisonVO comparison(Long id);

    /**
     * 定标（支持分项定标，两行可选不同供应商）：0询价中 -> 10已定标。
     * <ul>
     *   <li>perItem 必须覆盖全部明细各一次，中标供应商必须有对应报价；</li>
     *   <li>明细回写 中标供应商/中标单价/中标交期；</li>
     *   <li>请购明细已转数量条件 UPDATE 回写（数据库兜底防累计超转），请购单推进为已转采购。</li>
     * </ul>
     *
     * @param id   询比价单 ID
     * @param form 分项定标结果
     */
    void award(Long id, RfqAwardForm form);

    /**
     * 下推采购订单草稿：按定标供应商分组组装（物料/数量=定标量/单价=中标价/
     * 交货日期=定标日+中标交期），一供应商一单，备注记录「询比价 IN 单号生成」；
     * 调既有 {@link PurOrderService#create} 落库，RFQ 明细回写 pur_order_id（仅允许一次）。
     *
     * @param id 询比价单 ID
     * @return 生成的订单列表
     */
    RfqToOrderVO toOrders(Long id);

    /**
     * 取消询比价单：0询价中 -> 30已取消（终态）。已定标不可取消。
     *
     * @param id 询比价单 ID
     */
    void cancel(Long id);

    /**
     * 删除询比价单（仅询价中，逻辑删除主表/供应商/明细/报价）。
     *
     * @param id 询比价单 ID
     */
    void delete(Long id);
}
