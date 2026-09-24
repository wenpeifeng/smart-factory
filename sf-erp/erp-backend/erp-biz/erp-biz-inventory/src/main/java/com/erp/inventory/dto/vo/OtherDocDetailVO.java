package com.erp.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 其他入库/出库单详情 VO（头 + 明细行）。
 */
@Data
public class OtherDocDetailVO {

    private Long id;

    /** 单号（OI.../OO...） */
    private String no;

    private Long warehouseId;

    /** 入/出库原因 */
    private String reason;

    /** 状态：0草稿 1已审核 */
    private Integer status;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 明细行 */
    private List<LineVO> lines;

    /** 明细行（详情用，带物料编码/名称） */
    @Data
    public static class LineVO {

        private Long id;

        private Long materialId;
        private String materialCode;
        private String materialName;

        /** 批次号（非批次物料为空串；出库单为空=FIFO 自动分配） */
        private String batchNo;

        /** 数量（正数） */
        private BigDecimal qty;

        /** 行备注 */
        private String remark;
    }
}
