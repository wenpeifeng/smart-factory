package com.erp.basedata.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计量单位响应 VO。
 * <p>不暴露审计字段（createBy/updateBy/deleted），仅保留业务与追溯字段。
 */
@Data
public class BaseUnitVO {

    /** 主键 */
    private Long id;

    /** 换算组编码 */
    private String groupCode;

    /** 单位名称 */
    private String name;

    /** 换算比率（相对组内基准单位） */
    private BigDecimal rate;

    /** 是否基准单位：1-是 0-否 */
    private Integer isBase;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 从实体转换。
     *
     * @param entity 计量单位实体
     * @return VO
     */
    public static BaseUnitVO from(com.erp.basedata.entity.BaseUnit entity) {
        BaseUnitVO vo = new BaseUnitVO();
        vo.setId(entity.getId());
        vo.setGroupCode(entity.getGroupCode());
        vo.setName(entity.getName());
        vo.setRate(entity.getRate());
        vo.setIsBase(entity.getIsBase());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
