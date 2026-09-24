package com.erp.hr.dto.vo;

import lombok.Data;

/**
 * 物料简要信息（选择器，来源 base_material 只读）。
 */
@Data
public class MaterialBriefVO {

    private Long id;

    private String code;

    private String name;

    private String spec;
}
