package com.mes.execution.service;

import com.mes.common.result.PageResult;
import com.mes.execution.dto.form.ReworkScrapForm;
import com.mes.execution.dto.vo.ReworkScrapVO;

/**
 * 返工/报废单服务。
 */
public interface ReworkScrapService {

    /**
     * 创建返工/报废单（OPEN）。
     *
     * @param form 表单
     * @return 单据 id
     */
    Long create(ReworkScrapForm form);

    /**
     * 处理完成（OPEN → CLOSED）。
     *
     * @param id           单据 id
     * @param handleRemark 处理说明
     */
    void handle(Long id, String handleRemark);

    /**
     * 分页查询。
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @param keyword  单号/工单号模糊
     * @param type     REWORK/SCRAP
     * @param status   OPEN/CLOSED
     * @return 分页结果
     */
    PageResult<ReworkScrapVO> page(long pageNum, long pageSize, String keyword, String type, String status);
}
