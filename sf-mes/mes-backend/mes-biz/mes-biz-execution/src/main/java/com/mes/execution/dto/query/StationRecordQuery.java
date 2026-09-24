package com.mes.execution.dto.query;

import lombok.Data;

/**
 * 过站记录分页查询对象。
 */
@Data
public class StationRecordQuery {
    /** 页码 */
    private long pageNum = 1;
    /** 页大小 */
    private long pageSize = 10;
    /** 关键字（记录号/工单号/SN 模糊） */
    private String keyword;
    /** 工单 ID */
    private Long workorderId;
    /** 工序任务 ID */
    private Long woStepId;
    /** 过站结果 PASS/FAIL */
    private String result;
}
