package com.erp.purchase.dto.query;

import com.erp.framework.mybatis.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 询比价单分页查询条件（单号模糊/状态/来源请购单）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurRfqQuery extends PageQuery {

    /** 询比价单号模糊 */
    private String keyword;

    /** 状态：0询价中 10已定标 30已取消 */
    private Integer status;

    /** 来源请购单 ID */
    private Long requisitionId;

    /** 供应商 ID（按被邀请供应商过滤） */
    private Long supplierId;
}
