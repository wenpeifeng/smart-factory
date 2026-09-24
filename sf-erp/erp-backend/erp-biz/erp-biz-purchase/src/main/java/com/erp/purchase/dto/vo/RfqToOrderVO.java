package com.erp.purchase.dto.vo;

import lombok.Data;

import java.util.List;

/**
 * 询比价【下推采购订单】结果 VO：按定标供应商分组，一供应商生成一张订单草稿。
 */
@Data
public class RfqToOrderVO {

    /** 询比价单号（来源） */
    private String rfqNo;

    /** 生成的订单列表（一供应商一单） */
    private List<CreatedOrder> orders;

    /**
     * 生成的采购订单草稿。
     */
    @Data
    public static class CreatedOrder {

        /** 供应商 ID */
        private Long supplierId;

        /** 供应商名称 */
        private String supplierName;

        /** 采购订单 ID（草稿） */
        private Long orderId;

        /** 采购订单号 */
        private String orderNo;
    }
}
