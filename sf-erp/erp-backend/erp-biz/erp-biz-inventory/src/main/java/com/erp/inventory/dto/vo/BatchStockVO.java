package com.erp.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 批次余额行 VO（批次余额清单 / 移动端拣货建议）。
 * <p>
 * 只包含 batch_no != '' 的批次行（V421 起按批次聚合：未上架行+库位行合计）；
 * 库龄 = 今天 - 批次最早首入日期；效期取批次内最早（FEFO 建议依据），
 * 默认按效期升序（无批效期的批次按首入时间兜底）返回。
 */
@Data
public class BatchStockVO {

    /** inv_stock.id（聚合行取批次内最小行 id） */
    private Long id;

    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;

    private Long materialId;
    private String materialCode;
    private String materialName;

    /** 批次号 */
    private String batchNo;

    /** 批次现存量（未上架行 + 全部库位行合计） */
    private BigDecimal qty;

    /** 批次首次入库时间（批次内最早） */
    private LocalDateTime firstInAt;

    /** 批次效期（批次内最早；可 null，V422） */
    private LocalDate expiryDate;

    /** 库龄（天）= 当前日期 - 首次入库日期 */
    private Integer ageDays;
}
