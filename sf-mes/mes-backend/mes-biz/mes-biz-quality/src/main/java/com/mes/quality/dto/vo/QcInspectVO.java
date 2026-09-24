package com.mes.quality.dto.vo;

import com.mes.quality.entity.QcInspect;
import com.mes.quality.entity.QcInspectItem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 检验单 VO（列表与详情共用）。
 */
@Data
public class QcInspectVO {
    private Long id;
    private String inspectNo;
    private String inspectType;
    private String sourceType;
    private String sourceNo;
    private Long templateId;
    private Long workorderId;
    private Long woStepId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private Long supplierId;
    private String batchNo;
    private BigDecimal sampleQty;
    private BigDecimal qualifiedQty;
    private BigDecimal unqualifiedQty;
    private Integer conclusion;
    private String inspectorName;
    private LocalDate inspectDate;
    private Integer status;
    private LocalDateTime submitTime;
    private String remark;
    /** 项目行（详情时填充） */
    private List<ItemVO> items;

    /**
     * 实体转 VO。
     *
     * @param e 检验单实体
     * @return VO
     */
    public static QcInspectVO from(QcInspect e) {
        QcInspectVO vo = new QcInspectVO();
        vo.setId(e.getId());
        vo.setInspectNo(e.getInspectNo());
        vo.setInspectType(e.getInspectType());
        vo.setSourceType(e.getSourceType());
        vo.setSourceNo(e.getSourceNo());
        vo.setTemplateId(e.getTemplateId());
        vo.setWorkorderId(e.getWorkorderId());
        vo.setWoStepId(e.getWoStepId());
        vo.setMaterialId(e.getMaterialId());
        vo.setSupplierId(e.getSupplierId());
        vo.setBatchNo(e.getBatchNo());
        vo.setSampleQty(e.getSampleQty());
        vo.setQualifiedQty(e.getQualifiedQty());
        vo.setUnqualifiedQty(e.getUnqualifiedQty());
        vo.setConclusion(e.getConclusion());
        vo.setInspectorName(e.getInspectorName());
        vo.setInspectDate(e.getInspectDate());
        vo.setStatus(e.getStatus());
        vo.setSubmitTime(e.getSubmitTime());
        vo.setRemark(e.getRemark());
        return vo;
    }

    /** 检验单项目 VO */
    @Data
    public static class ItemVO {
        private Long id;
        private String itemName;
        private String standardValue;
        private BigDecimal lowerLimit;
        private BigDecimal upperLimit;
        private String inspectMethod;
        private BigDecimal sampleRate;
        private String actualValue;
        private Integer lineResult;
        private Integer seq;

        /**
         * 实体转 VO。
         *
         * @param e 检验单项目实体
         * @return VO
         */
        public static ItemVO from(QcInspectItem e) {
            ItemVO vo = new ItemVO();
            vo.setId(e.getId());
            vo.setItemName(e.getItemName());
            vo.setStandardValue(e.getStandardValue());
            vo.setLowerLimit(e.getLowerLimit());
            vo.setUpperLimit(e.getUpperLimit());
            vo.setInspectMethod(e.getInspectMethod());
            vo.setSampleRate(e.getSampleRate());
            vo.setActualValue(e.getActualValue());
            vo.setLineResult(e.getLineResult());
            vo.setSeq(e.getSeq());
            return vo;
        }
    }
}
