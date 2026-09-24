package com.mes.basedata.dto.query;

import lombok.Data;

/**
 * 物料分页查询对象。
 * <p>GET 参数绑定 POJO；categoryId 命中该分类及其全部子孙分类。
 */
@Data
public class MaterialPageQuery {

    /** 页码，从 1 开始 */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 10;

    /** 关键词，模糊匹配物料编码或名称 */
    private String keyword;

    /** 物料分类 ID（含子孙分类） */
    private Long categoryId;

    /** 状态：1-启用 0-停用，null=全部 */
    private Integer status;
}
