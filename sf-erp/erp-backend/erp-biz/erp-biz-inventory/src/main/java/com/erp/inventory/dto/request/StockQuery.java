package com.erp.inventory.dto.request;

import lombok.Data;

/**
 * 即时库存分页查询参数。
 * <p>
 * zero 取值：all=全部（默认）/ hide=隐藏零库存 / only=仅看零库存；
 * warningOnly=true 时仅返回低于安全库存的行。
 */
@Data
public class StockQuery {

    /** 页码，从 1 开始 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 仓库 ID 精确过滤 */
    private Long warehouseId;

    /** 物料模糊搜索（匹配物料编码/名称） */
    private String keyword;

    /** 零库存过滤：all/hide/only */
    private String zero = "all";

    /** 是否仅看低于安全库存（预警）的行 */
    private Boolean warningOnly = Boolean.FALSE;
}
