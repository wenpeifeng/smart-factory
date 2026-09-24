package com.erp.org.dto.request;

import lombok.Data;

/**
 * 工厂档案分页查询（名称模糊 / 状态）。
 */
@Data
public class FactoryQuery {

    /** 页码（从 1 开始） */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 10;

    /** 工厂名称/编码模糊搜索 */
    private String name;

    /** 状态：1 启用 0 停用（空=全部） */
    private Integer status;
}
