package com.erp.inventory.dto.request;

import lombok.Data;

/**
 * WMS 作业任务/波次/ABC 通用查询对象（分页 + 筛选）。
 * <p>三类列表共用（字段按需忽略），避免 request 类爆炸。
 */
@Data
public class WmsTaskQuery {

    /** 页码（1 起） */
    private long pageNum = 1;

    /** 页大小 */
    private long pageSize = 10;

    /** 任务类型（PUTAWAY/PICK/MOVE/REPLENISH） */
    private String taskType;

    /** 任务状态（0/10/20/30/40）；波次查询时为波次状态（0/10/20） */
    private Integer status;

    /** 仓库 ID */
    private Long warehouseId;

    /** 执行人用户 ID */
    private Long assignee;

    /** 关键词（任务号/来源单号/物料编码名称） */
    private String keyword;

    /** ABC 分级（A/B/C，ABC 列表用） */
    private String level;
}
