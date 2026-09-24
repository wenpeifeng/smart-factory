package com.erp.mrp.service;

import com.erp.common.result.PageResult;
import com.erp.mrp.dto.form.OutsourceForm;
import com.erp.mrp.dto.form.OutsourceReceiveForm;
import com.erp.mrp.dto.vo.OutsourceVO;

/**
 * 委外加工服务（简化模型）：草稿 → 发料扣库存 → 完工入库+加工费应付。
 */
public interface MrpOutsourceService {

    /**
     * 分页查询委外单。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  成品物料编码/名称关键词，可空
     * @param status   状态过滤，可空
     * @return 委外单分页（含物料/供应商回显）
     */
    PageResult<OutsourceVO> page(long pageNum, long pageSize, String keyword, String status);

    /**
     * 委外单详情（含发料明细）。
     *
     * @param id 委外单ID
     * @return 委外单 VO
     * @throws com.erp.common.exception.BusinessException 不存在
     */
    OutsourceVO detail(Long id);

    /**
     * 新建草稿委外单（按 BOM 自动带出发料明细）。
     *
     * @param form 表单
     * @return 新委外单ID
     * @throws com.erp.common.exception.BusinessException 物料/供应商不存在 / 无有效BOM
     */
    Long create(OutsourceForm form);

    /**
     * 修改草稿委外单（明细按新参数重建）。
     *
     * @param id   委外单ID
     * @param form 表单
     * @throws com.erp.common.exception.BusinessException 非草稿 / 校验失败
     */
    void update(Long id, OutsourceForm form);

    /**
     * 删除草稿委外单。
     *
     * @param id 委外单ID
     * @throws com.erp.common.exception.BusinessException 非草稿
     */
    void delete(Long id);

    /**
     * 发料：按发料明细扣减发料仓库库存，写 inv_movement(OUT_USE)，状态 → 已发料。
     *
     * @param id 委外单ID
     * @throws com.erp.common.exception.BusinessException 状态不合法 / 未指定发料仓库 / 库存不足
     */
    void issueMaterial(Long id);

    /**
     * 完工入库：成品入库（OUT_IN）并按 数量×加工费单价 写应付 fin_payable（财务集成点），状态 → 已入库。
     *
     * @param id   委外单ID
     * @param form 入库表单
     * @throws com.erp.common.exception.BusinessException 状态不合法 / 超量 / 应付表未就绪
     */
    void receive(Long id, OutsourceReceiveForm form);
}
