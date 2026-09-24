package com.erp.purchase.service;

import com.erp.common.result.PageResult;
import com.erp.purchase.dto.form.PurRequisitionForm;
import com.erp.purchase.dto.query.PurRequisitionQuery;
import com.erp.purchase.dto.vo.PurRequisitionVO;

/**
 * 请购单 Service。
 * <p>
 * 状态机（只允许正向流转）：0草稿 -> 10已提交 -> 20已转采购；
 * 10/20 可人工关闭为 30已关闭（终态）。修改/删除仅限草稿。
 * 已转采购（20）与明细 ordered_qty 由询比价定标事务驱动（PurRfqService#award），
 * 本 Service 不提供手工改已转数量的入口。
 */
public interface PurRequisitionService {

    /**
     * 分页查询请购单（单号模糊/状态/部门/物料筛选）。
     *
     * @param query 查询条件
     * @return 请购单分页（含部门/请购人名称，不含明细）
     */
    PageResult<PurRequisitionVO> page(PurRequisitionQuery query);

    /**
     * 请购单详情（主表 + 明细，含物料编码/名称/规格/单位与已转采购数量）。
     *
     * @param id 请购单 ID
     * @return 请购单详情
     * @throws com.erp.common.exception.BusinessException 请购单不存在
     */
    PurRequisitionVO detail(Long id);

    /**
     * 新增请购单（草稿）。单号 PR+yyyyMMdd+4 位流水（Redis）自动生成。
     *
     * @param form 请购表单
     * @return 新请购单 ID
     */
    Long create(PurRequisitionForm form);

    /**
     * 修改请购单（仅草稿可改，明细整体重写）。
     *
     * @param id   请购单 ID
     * @param form 请购表单
     */
    void update(Long id, PurRequisitionForm form);

    /**
     * 提交请购单：0草稿 -> 10已提交（条件 UPDATE 防并发重复提交）。
     *
     * @param id 请购单 ID
     */
    void submit(Long id);

    /**
     * 关闭请购单：10已提交/20已转采购 -> 30已关闭（终态，余量不再采购）。
     *
     * @param id 请购单 ID
     */
    void close(Long id);

    /**
     * 删除请购单（仅草稿，逻辑删除主表+明细）。
     *
     * @param id 请购单 ID
     */
    void delete(Long id);
}
