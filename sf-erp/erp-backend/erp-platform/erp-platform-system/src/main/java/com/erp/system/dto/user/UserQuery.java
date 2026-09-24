package com.erp.system.dto.user;

import lombok.Data;

/**
 * 用户分页查询条件。
 */
@Data
public class UserQuery {

    /** 关键词：账号/姓名模糊匹配 */
    private String keyword;

    /** 部门 ID 精确过滤 */
    private Long deptId;

    /** 状态过滤：1-启用 0-停用 */
    private Integer status;

    /** 页码，从 1 开始 */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 10;
}
