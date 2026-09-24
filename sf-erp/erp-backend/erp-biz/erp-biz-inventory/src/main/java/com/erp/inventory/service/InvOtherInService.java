package com.erp.inventory.service;

import com.erp.common.result.PageResult;
import com.erp.inventory.dto.form.OtherInForm;
import com.erp.inventory.dto.request.OtherDocQuery;
import com.erp.inventory.dto.vo.OtherDocDetailVO;
import com.erp.inventory.dto.vo.OtherDocVO;

/**
 * 其他入库单服务（无来源的手工入库：草稿 → 审核 → 过账库存）。
 */
public interface InvOtherInService {

    /**
     * 新建其他入库单（草稿，不动库存）。
     *
     * @param form 入库表单（仓库 + 多行物料数量 + 原因）
     * @return 新单据 ID
     */
    Long create(OtherInForm form);

    /**
     * 审核过账：草稿 → 已审核，逐行调
     * {@link StockService#changeStock(Long, Long, java.math.BigDecimal, com.erp.inventory.constant.InvBizType, Long, String)}
     * 增加库存（OTH_IN），整体一个事务，失败回滚。
     *
     * @param id 单据 ID
     * @throws com.erp.common.exception.BusinessException 单据不存在 / 已审核过 / 状态非法
     */
    void audit(Long id);

    /**
     * 删除单据（仅草稿可删，逻辑删除头+行；已审核单据不可删）。
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
