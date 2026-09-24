package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 委外加工单 VO（详情含 items）。
 */
@Data
public class OutsourceVO implements Serializable {

    /** 委外单ID */
    private Long id;

    /** 委外单号 */
    private String orderNo;

    /** 供应商ID */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 成品物料ID */
    private Long materialId;

    /** 成品物料编码 */
    private String materialCode;

    /** 成品物料名称 */
    private String materialName;

    /** 委外数量 */
    private BigDecimal qty;

    /** BOMID */
    private Long bomId;

    /** BOM版本 */
    private String bomVersion;

    /** 发料仓库 */
    private Long issueWarehouseId;

    /** 完工入库仓库 */
    private Long inWarehouseId;

    /** 加工费单价 */
    private BigDecimal feeUnitPrice;

    /** 状态：DRAFT/ISSUED/RECEIVED */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 发料明细（详情时返回） */
    private List<OutsourceItemVO> items;
}
