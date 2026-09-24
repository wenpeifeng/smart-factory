package com.erp.system.dto.dict;

import lombok.Data;

/**
 * 字典类型分页查询条件。
 */
@Data
public class DictTypeQuery {

    /** 关键词：名称/编码模糊匹配 */
    private String keyword;

    /** 状态过滤：1-启用 0-停用 */
    private Integer status;

    /** 页码，从 1 开始 */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 10;
}
