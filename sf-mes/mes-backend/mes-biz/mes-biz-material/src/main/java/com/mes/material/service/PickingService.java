package com.mes.material.service;

import com.mes.common.result.PageResult;
import com.mes.material.dto.form.PickingForm;
import com.mes.material.dto.vo.PickingVO;

/**
 * 领料单服务。
 * <p>审核（DRAFT→AUDITED）事务内：线边仓入库 → 回写工单用料已领数 → 回传 ERP。
 */
public interface PickingService {

    /**
     * 创建领料单（DRAFT，带行项目）。
     *
     * @param form 表单
     * @return 领料单 id
     */
    Long create(PickingForm form);

    /**
     * 删除领料单（仅 DRAFT）。
     *
     * @param id 领料单 id
     */
    void delete(Long id);

    /**
     * 审核领料单（生效并回传 ERP）。
     *
     * @param id 领料单 id
     */
    void audit(Long id);

    /**
     * 分页查询。
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @param keyword  单号/工单号模糊
     * @param status   DRAFT/AUDITED
     * @return 分页结果
     */
    PageResult<PickingVO> page(long pageNum, long pageSize, String keyword, String status);

    /**
     * 详情（含行项目）。
     *
     * @param id 领料单 id
     * @return 详情 VO
     */
    PickingVO detail(Long id);
}
