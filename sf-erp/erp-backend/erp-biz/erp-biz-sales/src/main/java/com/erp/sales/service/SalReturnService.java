package com.erp.sales.service;

import com.erp.common.result.PageResult;
import com.erp.sales.dto.form.SalReturnForm;
import com.erp.sales.dto.vo.SalReturnDetailBundle;
import com.erp.sales.dto.vo.SalReturnVO;

/**
 * 销售退货单 Service。
 * <p>
 * 退货引用"已审核"销售出库单；审核在一个事务内完成：
 * 回补库存 → 写正数库存流水 → 生成红字（负金额）应收 → 回写出库行已退数量。
 */
public interface SalReturnService {

    /**
     * 分页查询销售退货单。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  关键词（退货单号/出库单号/客户名称，可空）
     * @param status   状态（可空）
     * @return 退货单分页
     */
    PageResult<SalReturnVO> page(long pageNum, long pageSize, String keyword, Integer status);

    /**
     * 查询退货单详情（表头 + 明细行）。
     *
     * @param id 退货单 ID
     * @return 详情聚合 VO
     * @throws com.erp.common.exception.BusinessException 退货单不存在
     */
    SalReturnDetailBundle getDetail(Long id);

    /**
     * 新增销售退货单（草稿态，引用销售出库单）。
     *
     * @param form 退货表单
     * @return 新退货单 ID
     * @throws com.erp.common.exception.BusinessException 出库单不可退货 / 数量超可退
     */
    Long create(SalReturnForm form);

    /**
     * 审核销售退货单（执行单据：回补库存/写流水/生红字应收/回写出库行）。
     *
     * @param id 退货单 ID
     * @throws com.erp.common.exception.BusinessException 非草稿态 / 超出库可退数量
     */
    void audit(Long id);

    /**
     * 删除销售退货单（仅草稿可删，逻辑删除，含明细行）。
     *
     * @param id 退货单 ID
     * @throws com.erp.common.exception.BusinessException 退货单不存在或非草稿态
     */
    void delete(Long id);
}
