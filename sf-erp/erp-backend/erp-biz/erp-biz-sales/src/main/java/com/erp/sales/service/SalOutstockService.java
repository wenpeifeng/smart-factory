package com.erp.sales.service;

import com.erp.common.result.PageResult;
import com.erp.sales.dto.form.SalOutstockForm;
import com.erp.sales.dto.vo.SalOutstockDetailBundle;
import com.erp.sales.dto.vo.SalOutstockVO;

/**
 * 销售出库单 Service。
 * <p>
 * 出库单从"已审核/部分出库"销售订单选单生成；审核在一个事务内完成：
 * 扣减库存（不足整体回滚）→ 写负数库存流水 → 生成应收 → 回写订单出库数量与状态。
 */
public interface SalOutstockService {

    /**
     * 分页查询销售出库单。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  关键词（出库单号/订单号/客户名称，可空）
     * @param status   状态（可空）
     * @return 出库单分页
     */
    PageResult<SalOutstockVO> page(long pageNum, long pageSize, String keyword, Integer status);

    /**
     * 查询出库单详情（表头 + 明细行）。
     *
     * @param id 出库单 ID
     * @return 详情聚合 VO
     * @throws com.erp.common.exception.BusinessException 出库单不存在
     */
    SalOutstockDetailBundle getDetail(Long id);

    /**
     * 新增销售出库单（草稿态，从销售订单选单）。
     *
     * @param form 出库表单
     * @return 新出库单 ID
     * @throws com.erp.common.exception.BusinessException 订单不可出库 / 数量超剩余
     */
    Long create(SalOutstockForm form);

    /**
     * 审核销售出库单（执行单据：扣库存/写流水/生应收/回写订单）。
     *
     * @param id 出库单 ID
     * @throws com.erp.common.exception.BusinessException 非草稿态 / 库存不足 / 超订单剩余数量
     */
    void audit(Long id);

    /**
     * 删除销售出库单（仅草稿可删，逻辑删除，含明细行）。
     *
     * @param id 出库单 ID
     * @throws com.erp.common.exception.BusinessException 出库单不存在或非草稿态
     */
    void delete(Long id);
}
