package com.erp.qc.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 检验模板 VO（列表行 + 详情）。
 */
@Data
public class QcTemplateVO {

    private Long id;

    /** 模板编码 */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 适用物料分类 ID */
    private Long categoryId;

    /** 适用物料分类名称（空=全部分类通用） */
    private String categoryName;

    /** 检验类型：IQC/IPQC/FQC/OQC */
    private String inspectType;

    /** 状态：1启用 0停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 检验项目（详情接口返回） */
    private List<QcTemplateItemVO> items;

    /**
     * 检验项目 VO。
     */
    @Data
    public static class QcTemplateItemVO {

        private Long id;

        /** 检验项目名称 */
        private String itemName;

        /** 标准值 */
        private String standardValue;

        /** 标准下限 */
        private java.math.BigDecimal lowerLimit;

        /** 标准上限 */
        private java.math.BigDecimal upperLimit;

        /** 检验方法 */
        private String inspectMethod;

        /** 抽检比例 % */
        private java.math.BigDecimal sampleRate;

        /** 排序 */
        private Integer seq;

        /** 备注 */
        private String remark;
    }
}
