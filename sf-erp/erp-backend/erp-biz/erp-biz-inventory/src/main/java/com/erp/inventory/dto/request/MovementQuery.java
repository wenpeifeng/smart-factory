package com.erp.inventory.dto.request;

import lombok.Data;

import java.time.LocalDate;

/**
 * 库存流水分页查询参数（日期/类型/物料/仓库/单号）。
 */
@Data
public class MovementQuery {

    /** 页码，从 1 开始 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 业务类型（PUR_IN/OTH_OUT/... 见 InvBizType） */
    private String bizType;

    /** 开始日期（含），格式 yyyy-MM-dd */
    private LocalDate beginDate;

    /** 结束日期（含），格式 yyyy-MM-dd */
    private LocalDate endDate;

    /** 物料 ID 精确过滤 */
    private Long materialId;

    /** 仓库 ID 精确过滤 */
    private Long warehouseId;

    /** 模糊搜索：业务单号 / 物料编码 / 物料名称 */
    private String keyword;
}
