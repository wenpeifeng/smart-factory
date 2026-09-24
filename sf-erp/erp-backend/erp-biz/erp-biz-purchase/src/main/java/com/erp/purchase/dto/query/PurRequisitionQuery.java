package com.erp.purchase.dto.query;

import com.erp.framework.mybatis.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 请购单分页查询条件（状态/部门/物料筛选 + 单号模糊）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurRequisitionQuery extends PageQuery {

    /** 请购单号模糊 */
    private String keyword;

    /** 状态：0草稿 10已提交 20已转采购 30已关闭 */
    private Integer status;

    /** 请购部门 ID */
    private Long deptId;

    /** 物料 ID（按明细包含的物料过滤，EXISTS 子查询） */
    private Long materialId;
}
