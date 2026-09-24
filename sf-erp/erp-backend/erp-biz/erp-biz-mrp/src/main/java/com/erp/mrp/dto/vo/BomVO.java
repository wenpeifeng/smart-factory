package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * BOM VO：列表行（不含 items）与详情行（含 items）复用。
 */
@Data
public class BomVO implements Serializable {

    /** BOMID */
    private Long id;

    /** 成品物料ID */
    private Long materialId;

    /** 成品物料编码 */
    private String materialCode;

    /** 成品物料名称 */
    private String materialName;

    /** 计量单位 */
    private String unit;

    /** 版本号 */
    private String version;

    /** 状态：DRAFT/ACTIVE/DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 子件数（列表展示） */
    private Long itemCount;

    /** 子件明细（详情时返回） */
    private List<BomItemVO> items;
}
