package com.erp.purchase.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购退货单 VO（列表行 + 详情）。
 */
@Data
public class PurReturnVO {

    private Long id;

    /** 退货单号 */
    private String returnNo;

    /** 来源入库单 ID */
    private Long instockId;

    /** 来源入库单号 */
    private String instockNo;

    /** 供应商 ID */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 退货仓库 ID */
    private Long warehouseId;

    /** 仓库名称 */
    private String warehouseName;

    /** 退货日期 */
    private LocalDate returnDate;

    /** 价税合计（冲减口径） */
    private BigDecimal totalAmount;

    /** 状态：0草稿 10已审核 */
    private Integer status;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 明细（详情接口返回） */
    private List<PurReturnDetailVO> details;

    /**
     * 采购退货明细 VO。
     */
    @Data
    public static class PurReturnDetailVO {

        private Long id;

        /** 来源入库明细 ID */
        private Long instockDetailId;

        /** 物料 ID */
        private Long materialId;

        /** 物料编码 */
        private String materialCode;

        /** 物料名称 */
        private String materialName;

        /** 规格 */
        private String spec;

        /** 计量单位 */
        private String unit;

        /** 退货数量 */
        private BigDecimal qty;

        /** 含税单价 */
        private BigDecimal price;

        /** 税率（百分数） */
        private BigDecimal taxRate;

        /** 含税金额 */
        private BigDecimal amount;

        /** 行备注 */
        private String remark;
    }
}
