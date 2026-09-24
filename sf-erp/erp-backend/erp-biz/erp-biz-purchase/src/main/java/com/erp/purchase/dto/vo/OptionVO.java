package com.erp.purchase.dto.vo;

import lombok.Data;

/**
 * 基础资料下拉选项 VO（供应商/物料/仓库的 id+编码+名称轻量投影）。
 * <p>
 * basedata 模块标准接口并行开发中，采购单据选单暂由本模块只读查询 base_* 表提供选项，
 * 联调后可切换至 basedata 标准接口（见 docs/modules/purchase.md）。
 */
@Data
public class OptionVO {

    /** 主键 */
    private Long id;

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 规格（仅物料有值） */
    private String spec;

    /** 计量单位（仅物料有值） */
    private String unit;
}
