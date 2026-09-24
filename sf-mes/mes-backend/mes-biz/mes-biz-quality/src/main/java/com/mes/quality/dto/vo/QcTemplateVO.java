package com.mes.quality.dto.vo;

import com.mes.quality.entity.QcTemplate;
import com.mes.quality.entity.QcTemplateItem;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 检验模板 VO（列表与详情共用，详情含项目行）。
 */
@Data
public class QcTemplateVO {
    private Long id;
    private String templateCode;
    private String templateName;
    private Long categoryId;
    private String inspectType;
    private Integer status;
    private String remark;
    /** 项目行（详情时填充） */
    private List<ItemVO> items;

    /**
     * 实体转 VO。
     *
     * @param e 模板实体
     * @return VO
     */
    public static QcTemplateVO from(QcTemplate e) {
        QcTemplateVO vo = new QcTemplateVO();
        vo.setId(e.getId());
        vo.setTemplateCode(e.getTemplateCode());
        vo.setTemplateName(e.getTemplateName());
        vo.setCategoryId(e.getCategoryId());
        vo.setInspectType(e.getInspectType());
        vo.setStatus(e.getStatus());
        vo.setRemark(e.getRemark());
        return vo;
    }

    /** 模板项目 VO */
    @Data
    public static class ItemVO {
        private Long id;
        private String itemName;
        private String standardValue;
        private BigDecimal lowerLimit;
        private BigDecimal upperLimit;
        private String inspectMethod;
        private BigDecimal sampleRate;
        private Integer seq;
        private String remark;

        /**
         * 实体转 VO。
         *
         * @param e 模板项目实体
         * @return VO
         */
        public static ItemVO from(QcTemplateItem e) {
            ItemVO vo = new ItemVO();
            vo.setId(e.getId());
            vo.setItemName(e.getItemName());
            vo.setStandardValue(e.getStandardValue());
            vo.setLowerLimit(e.getLowerLimit());
            vo.setUpperLimit(e.getUpperLimit());
            vo.setInspectMethod(e.getInspectMethod());
            vo.setSampleRate(e.getSampleRate());
            vo.setSeq(e.getSeq());
            vo.setRemark(e.getRemark());
            return vo;
        }
    }
}
