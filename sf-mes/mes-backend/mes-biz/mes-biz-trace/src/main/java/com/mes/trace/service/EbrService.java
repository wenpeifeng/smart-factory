package com.mes.trace.service;

import com.mes.common.result.PageResult;
import com.mes.trace.dto.vo.TraceEbrVO;

/**
 * 电子批记录服务（工单完工快照，生成后只读）。
 */
public interface EbrService {

    /**
     * 为工单生成电子批记录（已存在直接返回；聚合工艺/过站/用料/质检全数据为 JSON）。
     *
     * @param workorderId 工单 id
     * @return EBR id
     */
    Long generate(Long workorderId);

    /**
     * 分页查询 EBR。
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @param keyword  批记录号/工单号/批次号模糊
     * @return 分页结果
     */
    PageResult<TraceEbrVO> page(long pageNum, long pageSize, String keyword);

    /**
     * 详情（含 content JSON）。
     *
     * @param id EBR id
     * @return 详情 VO
     */
    TraceEbrVO detail(Long id);
}
