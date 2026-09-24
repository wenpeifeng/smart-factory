package com.erp.inventory.service;

import com.erp.common.result.PageResult;
import com.erp.inventory.dto.form.OtherOutForm;
import com.erp.inventory.dto.request.OtherDocQuery;
import com.erp.inventory.dto.vo.OtherDocDetailVO;
import com.erp.inventory.dto.vo.OtherDocVO;

/**
 * 其他出库单服务（无来源的手工出库：草稿 → 审核 → 过账库存）。
 */
public interface InvOtherOutService {

    /**
     * 新建其他出库单（草稿，不动库存）。
     *
     * @param form 出库表单（仓库 + 多行物料数量 + 原因）
     * @return 新单据 ID
     */
    Long create(OtherOutForm form);

    /**
     * 审核过账：草稿 → 已审核，逐行扣减库存（OTH_OUT，负数变动），
     * 整体一个事务，任一行库存不足全部回滚。
     *
     * @param id 单据 ID
     * @throws com.erp.common.exception.BusinessException 单据不存在 / 已审核过 / 库存不足
     */
    void audit(Long id);

    /**
     * 删除单据（仅草稿可删，逻辑删除头+行）。
     *
     * @param id 单据 ID
     */
    void delete(Long id);

    /**
     * 分页查询。
     */
    PageResult<OtherDocVO> page(OtherDocQuery query);

    /**
     * 单据详情（头 + 明细行）。
     *
     * @param id 单据 ID
     */
    OtherDocDetailVO detail(Long id);
}
