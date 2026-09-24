package com.erp.org.dto.raw;

import lombok.Data;

/**
 * base_warehouse 归属信息投影（basedata 模块表，只读 + factory_id 归属调整）。
 */
@Data
public class WarehouseRaw {

    /** 仓库 ID */
    private Long id;

    /** 仓库编码 */
    private String code;

    /** 仓库名称 */
    private String name;

    /** 所属工厂 ID（NULL=未归属，V961 后存量已回填总厂） */
    private Long factoryId;

    /** 仓库状态：1 启用 0 停用 */
    private Integer status;
}
