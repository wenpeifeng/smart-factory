package com.erp.system.dto.tenant;

import com.erp.framework.mybatis.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户分页查询条件（继承 {@link PageQuery}：pageNum/pageSize/sorts 排序白名单）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantQuery extends PageQuery {

    /** 关键词：编码/名称模糊匹配 */
    private String keyword;

    /** 状态过滤：1-启用 0-停用 */
    private Integer status;
}
