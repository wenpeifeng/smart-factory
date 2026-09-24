package com.mes.material.service;

import com.mes.common.result.PageResult;
import com.mes.material.dto.form.ReturnForm;
import com.mes.material.dto.vo.ReturnVO;

/**
 * 退料单服务。
 * <p>审核（DRAFT→AUDITED）事务内：线边仓扣减 → 回传 ERP 入库。
 */
public interface ReturnService {

    /**
     * 创建退料单（DRAFT）。
     *
     * @param form 表单
     * @return 退料单 id
     */
    Long create(ReturnForm form);

    /**
     * 删除退料单（仅 DRAFT）。
     *
     * @param id 退料单 id
     */
    void delete(Long id);

    /**
     * 审核退料单。
     *
     * @param id 退料单 id
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
    PageResult<ReturnVO> page(long pageNum, long pageSize, String keyword, String status);

    /**
     * 详情（含行项目）。
     *
     * @param id 退料单 id
     * @return 详情 VO
     */
    ReturnVO detail(Long id);
}
