package com.erp.mrp.service;

import com.erp.common.result.PageResult;
import com.erp.mrp.dto.form.ToPurchaseBatchForm;
import com.erp.mrp.dto.form.ToPurchaseForm;
import com.erp.mrp.dto.form.ToWorkOrderForm;
import com.erp.mrp.dto.vo.SuggestionVO;

/**
 * MRP 发补建议服务 —— 运算结果查询与转单（转生产 + 转采购）。
 * <p>
 * 转采购（mrp → purchase 接线，依赖方向单向）：调采购模块
 * {@code PurOrderService.create} 生成草稿（供应商可空待补填，
 * 物料/数量/建议交货日期=建议日期，备注记 MRP 批次与建议ID），
 * 成功后建议状态→CONVERTED 并回写 refType=PUR_ORDER/refId=采购订单ID。
 */
public interface MrpSuggestionService {

    /**
     * 分页查询建议。
     *
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @param batchNo  运算批次号过滤，可空
     * @param planType 建议类型过滤（PURCHASE/PRODUCE），可空
     * @param status   状态过滤，可空
     * @return 建议分页（含物料回显）
     */
    PageResult<SuggestionVO> page(long pageNum, long pageSize, String batchNo, String planType, String status);

    /**
     * 生产建议转生产订单（生成草稿单并回写建议引用）。
     *
     * @param id   建议ID
     * @param form 转单表单（默认入库仓库，可空）
     * @return 新生产订单ID
     * @throws com.erp.common.exception.BusinessException 类型/状态不合法 / 无有效BOM
     */
    Long toWorkOrder(Long id, ToWorkOrderForm form);

    /**
     * 采购建议转采购订单（单条：生成一张草稿，供应商可空待补填）。
     *
     * @param id   建议ID
     * @param form 转单表单（供应商/仓库均可空）
     * @return 新采购订单ID
     * @throws com.erp.common.exception.BusinessException 建议不存在 / 非采购类型 / 已转单
     */
    Long toPurchase(Long id, ToPurchaseForm form);

    /**
     * 采购建议批量转采购订单：同供应商（或均待定）合并生成一张草稿单，
     * 每条建议一行明细（物料/数量/建议交货日期=建议日期，单价 0 待补）。
     *
     * @param form 批量表单（ids 必填；供应商/仓库可空）
     * @return 新采购订单ID（一张单，N 行明细）
     * @throws com.erp.common.exception.BusinessException 任一建议不存在 / 非采购类型 / 已转单
     */
    Long toPurchaseBatch(ToPurchaseBatchForm form);
}
