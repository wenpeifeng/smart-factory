package com.erp.org.dto.vo;

import lombok.Data;

/**
 * 仓库归属行 VO（工厂下仓库清单 / 归属调整回显，base_warehouse 只读投影）。
 */
@Data
public class WarehouseBelongVO {

    /** 仓库 ID */
    private Long id;

    /** 仓库编码 */
    private String code;

    /** 仓库名称 */
    private String name;

    /** 仓库管理员 */
    private String manager;

    /** 所属工厂 ID */
    private Long factoryId;

    /** 所属工厂名称 */
    private String factoryName;

    /** 仓库状态：1 启用 0 停用 */
    private Integer status;
}
