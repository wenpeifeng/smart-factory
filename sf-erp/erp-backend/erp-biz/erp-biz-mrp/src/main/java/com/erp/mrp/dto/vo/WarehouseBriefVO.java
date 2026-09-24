package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 仓库简要信息（来自约定表 base_warehouse）。
 */
@Data
public class WarehouseBriefVO implements Serializable {

    /** 仓库ID */
    private Long id;

    /** 仓库名称 */
    private String name;
}
