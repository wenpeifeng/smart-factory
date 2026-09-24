package com.mes.basedata.dto.vo;

import com.mes.basedata.entity.BaseRouting;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工艺路线 VO。
 */
@Data
public class BaseRoutingVO {
    private Long id;
    private Long materialId;
    private String version;
    private String status;
    private String remark;
    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     *
     * @param e 工艺路线实体
     * @return VO
     */
    public static BaseRoutingVO from(BaseRouting e) {
        BaseRoutingVO vo = new BaseRoutingVO();
        vo.setId(e.getId());
        vo.setMaterialId(e.getMaterialId());
        vo.setVersion(e.getVersion());
        vo.setStatus(e.getStatus());
        vo.setRemark(e.getRemark());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}
