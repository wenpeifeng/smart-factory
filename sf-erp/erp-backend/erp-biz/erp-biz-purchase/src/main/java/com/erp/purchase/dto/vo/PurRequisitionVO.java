package com.erp.purchase.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 请购单 VO（列表行与详情通用，明细由详情填充）。
 */
@Data
public class PurRequisitionVO {

    /** 请购单 ID */
    private Long id;

    /** 请购单号 */
    private String reqNo;

    /** 请购部门 ID */
    private Long deptId;

    /** 请购部门名称 */
    private String deptName;

    /** 请购人 ID */
    private Long applicantId;

    /** 请购人姓名 */
    private String applicantName;

    /** 期望到货日期 */
    private LocalDate expectDate;

    /** 状态：0草稿 10已提交 20已转采购 30已关闭 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 明细行（详情接口填充） */
    private List<ItemVO> items;

    /**
     * 请购明细 VO（含物料编码/名称/规格/单位与已转采购数量）。
     */
    @Data
    public static class ItemVO {

        /** 明细 ID */
        private Long id;

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

        /** 请购数量 */
        private BigDecimal qty;

        /** 已转采购数量（询比价定标回写） */
        private BigDecimal orderedQty;

        /** 需求日期 */
        private LocalDate requireDate;

        /** 备注 */
        private String remark;
    }
}
