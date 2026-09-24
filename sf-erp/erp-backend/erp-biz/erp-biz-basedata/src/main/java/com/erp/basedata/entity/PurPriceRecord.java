package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购价格流水实体（表 pur_price_record，V131）。
 * <p>
 * 成交价库：已审核采购订单 / 采购入库单 / 定标 RFQ 由扫描器
 * （{@code PriceCollectScheduleConfig}）自动沉淀，亦可手工补录。
 * 价格口径为<b>不含税</b>单价；订单/入库明细的含税价按
 * {@code price / (1 + taxRate/100)} 换算（HALF_UP 4 位）后入库。
 * 幂等键：uk(source_type, source_detail_id)；手工行 sourceDetailId 为 NULL。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pur_price_record")
public class PurPriceRecord extends BaseEntity {

    /** 来源类型：采购订单 */
    public static final String SOURCE_ORDER = "ORDER";
    /** 来源类型：采购入库 */
    public static final String SOURCE_INSTOCK = "INSTOCK";
    /** 来源类型：询比价定标 */
    public static final String SOURCE_RFQ = "RFQ";
    /** 来源类型：合同/手工补录 */
    public static final String SOURCE_CONTRACT = "CONTRACT";

    /** 供应商 ID（base_supplier.id） */
    private Long supplierId;

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /** 成交单价（不含税），DECIMAL(18,4) */
    private BigDecimal price;

    /** 来源类型：ORDER/INSTOCK/RFQ/CONTRACT */
    private String sourceType;

    /** 来源单号（PO/RK/IN 前缀或合同号，可追溯） */
    private String sourceNo;

    /** 来源明细行 ID（订单行/入库行/询价行；手工补录为 NULL） */
    private Long sourceDetailId;

    /** 价格日期（单据业务日期） */
    private LocalDate priceDate;

    /** 备注 */
    private String remark;
}
