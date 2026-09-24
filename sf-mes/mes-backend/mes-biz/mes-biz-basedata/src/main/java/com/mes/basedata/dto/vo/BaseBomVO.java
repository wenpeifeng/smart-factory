package com.mes.basedata.dto.vo;

import com.mes.basedata.entity.BaseBom;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * BOM VO。
 */
@Data
public class BaseBomVO {
    private Long id;
    private Long materialId;
    private String version;
    private String status;
    private String remark;
    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     *
     * @param e BOM 实体
     * @return VO
     */
    public static BaseBomVO from(BaseBom e) {
        BaseBomVO vo = new BaseBomVO();
        vo.setId(e.getId());
        vo.setMaterialId(e.getMaterialId());
        vo.setVersion(e.getVersion());
        vo.setStatus(e.getStatus());
        vo.setRemark(e.getRemark());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}
