package com.mes.system.dto.dict;

import lombok.Data;

/**
 * 字典数据分页查询条件（管理页按类型过滤）。
 */
@Data
public class DictDataQuery {

    /** 所属类型编码（精确） */
    private String dictType;

    /** 关键词：标签/值模糊匹配 */
    private String keyword;

    /** 状态过滤：1-启用 0-停用 */
    private Integer status;

    /** 页码，从 1 开始 */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 10;
}
