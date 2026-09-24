package com.erp.org.dto.vo;

import com.erp.org.constant.OrgConstants;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 组织间调拨价目 VO（带物料/工厂名称回显）。
 */
@Data
public class TransferPriceVO {

    private Long id;

    /** 物料 ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 调出工厂 ID */
    private Long fromFactoryId;

    /** 调出工厂名称 */
    private String fromFactoryName;

    /** 调入工厂 ID */
    private Long toFactoryId;

    /** 调入工厂名称 */
    private String toFactoryName;

    /** 加价类型：RATE/FIXED */
    private String markupType;

    /** 加价类型中文 */
    private String markupTypeText;

    /** 加价值 */
    private BigDecimal markupValue;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 加价类型转中文（列表展示用） */
    public String getMarkupTypeText() {
        if (markupTypeText != null) {
            return markupTypeText;
        }
        return OrgConstants.MARKUP_RATE.equals(markupType) ? "加价率%" : "固定加价额";
    }
}
