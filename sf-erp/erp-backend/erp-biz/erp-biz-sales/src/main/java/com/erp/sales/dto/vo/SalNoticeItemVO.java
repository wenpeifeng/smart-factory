package com.erp.sales.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售发货通知单明细行 VO（JOIN base_material，含核销进度）。
 */
@Data
public class SalNoticeItemVO {

    /** 明细行 ID */
    private Long id;

    /** 通知单 ID */
    private Long noticeId;

    /** 来源订单明细 ID（空 = 直发行） */
    private Long orderDetailId;

    /** 物料 ID */
    private Long materialId;

    /** 物料编码（JOIN base_material） */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 规格型号 */
    private String materialSpec;

    /** 通知数量 */
    private BigDecimal qty;

    /** 已出库数量（核销累加，恒 ≤ qty） */
    private BigDecimal outQty;

    /** 剩余待出库数量 = qty − out_qty */
    private BigDecimal remainingQty;

    /** 行备注 */
    private String remark;
}
