package com.erp.org.dto.vo;

import com.erp.org.constant.OrgConstants;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 组织间调拨单 VO（列表行；详情子类带明细行）。
 */
@Data
public class TransferVO {

    private Long id;

    /** 调拨单号（IT...） */
    private String transferNo;

    /** 调出工厂 ID/名称 */
    private Long fromFactoryId;
    private String fromFactoryName;

    /** 调出仓库 ID/名称 */
    private Long fromWarehouseId;
    private String fromWarehouseName;

    /** 调入工厂 ID/名称 */
    private Long toFactoryId;
    private String toFactoryName;

    /** 调入仓库 ID/名称 */
    private Long toWarehouseId;
    private String toWarehouseName;

    /** 状态：0 草稿 1 已审核 2 已取消 */
    private Integer status;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 明细行数 */
    private Long lineCount;

    /** 数量合计 */
    private BigDecimal totalQty;

    /** 内部结算金额合计 */
    private BigDecimal totalAmount;

    /** 状态中文（前端 StatusTag 直接消费） */
    public String getStatusText() {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case OrgConstants.TRANSFER_DRAFT -> "草稿";
            case OrgConstants.TRANSFER_AUDITED -> "已审核";
            case OrgConstants.TRANSFER_CANCELLED -> "已取消";
            default -> String.valueOf(status);
        };
    }

    /** 详情 VO：调拨单头 + 明细行集合 */
    @Data
    public static class Detail extends TransferVO {

        /** 审核人（用户 ID） */
        private Long auditBy;

        /** 明细行 */
        private List<LineVO> lines;
    }

    /** 明细行 VO（带物料回显与价格快照） */
    @Data
    public static class LineVO {

        private Long id;

        /** 物料 ID */
        private Long materialId;

        /** 物料编码/名称 */
        private String materialCode;
        private String materialName;

        /** 调拨数量 */
        private BigDecimal qty;

        /** 成本单价（调出方最近加权价） */
        private BigDecimal costPrice;

        /** 内部结算单价 */
        private BigDecimal settlePrice;

        /** 内部结算金额 */
        private BigDecimal settleAmount;
    }
}
