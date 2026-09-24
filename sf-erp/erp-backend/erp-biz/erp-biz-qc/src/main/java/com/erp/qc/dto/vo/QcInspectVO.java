package com.erp.qc.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 检验单 VO（列表行 + 详情）。
 */
@Data
public class QcInspectVO {

    private Long id;

    /** 检验单号 */
    private String inspectNo;

    /** 检验类型：IQC/IPQC/FQC/OQC */
    private String inspectType;

    /** 来源单据类型：pur_instock/mrp_work_order/sal_return，空=手工 */
    private String sourceType;

    /** 来源单据 ID */
    private Long sourceId;

    /** 来源单号 */
    private String sourceNo;

    /** 套用的检验模板 ID */
    private Long templateId;

    /** 套用的检验模板名称 */
    private String templateName;

    /** 物料 ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 物料规格 */
    private String spec;

    /** 计量单位 */
    private String unit;

    /** 供应商 ID */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 抽检数量 */
    private BigDecimal sampleQty;

    /** 合格数量 */
    private BigDecimal qualifiedQty;

    /** 不合格数量 */
    private BigDecimal unqualifiedQty;

    /** 检验结论：1合格 2让步接收 3退货（草稿为空） */
    private Integer conclusion;

    /** 检验人 */
    private String inspectorName;

    /** 检验日期 */
    private LocalDate inspectDate;

    /** 状态：0草稿 10已检验 */
    private Integer status;

    /** 提交检验时间 */
    private LocalDateTime submitTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 检验项目明细（详情接口返回） */
    private List<QcInspectItemVO> items;

    /**
     * 检验项目明细 VO（模板快照 + 实检值）。
     */
    @Data
    public static class QcInspectItemVO {

        private Long id;

        /** 检验项目名称 */
        private String itemName;

        /** 标准值 */
        private String standardValue;

        /** 标准下限 */
        private BigDecimal lowerLimit;

        /** 标准上限 */
        private BigDecimal upperLimit;

        /** 检验方法 */
        private String inspectMethod;

        /** 抽检比例 % */
        private BigDecimal sampleRate;

        /** 实检值 */
        private String actualValue;

        /** 单项判定：1合格 0不合格 空=未判定 */
        private Integer lineResult;

        /** 排序 */
        private Integer seq;

        /** 备注 */
        private String remark;
    }
}
