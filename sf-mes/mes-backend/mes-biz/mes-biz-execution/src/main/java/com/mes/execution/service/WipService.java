package com.mes.execution.service;

import com.mes.common.result.PageResult;
import com.mes.execution.dto.vo.WipVO;

/**
 * 在制品（WIP）查询服务。
 */
public interface WipService {

    /**
     * 分页查询 WIP 汇总。
     *
     * @param pageNum   页码
     * @param pageSize  页大小
     * @param keyword   工单号模糊
     * @param workorderId 工单 ID
     * @return 分页结果
     */
    PageResult<WipVO> page(long pageNum, long pageSize, String keyword, Long workorderId);
}
