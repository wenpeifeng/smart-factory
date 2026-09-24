package com.mes.basedata.dto.vo;

import com.mes.basedata.entity.BaseSettleType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 结算方式响应 VO。
 */
@Data
public class BaseSettleTypeVO {

    /** 主键 */
    private Long id;

    /** 方式编码 */
    private String code;

    /** 方式名称 */
    private String name;

    /** 账期天数 */
    private Integer days;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 从实体转换。
     *
     * @param entity 结算方式实体
     * @return VO
     */
    public static BaseSettleTypeVO from(BaseSettleType entity) {
        BaseSettleTypeVO vo = new BaseSettleTypeVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setDays(entity.getDays());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
