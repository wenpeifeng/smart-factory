package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 物料简要信息（来自约定表 base_material，跨模块只读投影）。
 */
@Data
public class MaterialBriefVO implements Serializable {

    /** 物料ID */
    private Long id;

    /** 物料编码 */
    private String code;

    /** 物料名称 */
    private String name;

    /** 规格型号 */
    private String spec;

    /** 计量单位 */
    private String unit;
}
