package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 供应商简要信息（来自约定表 base_supplier）。
 */
@Data
public class SupplierBriefVO implements Serializable {

    /** 供应商ID */
    private Long id;

    /** 供应商名称 */
    private String name;
}
