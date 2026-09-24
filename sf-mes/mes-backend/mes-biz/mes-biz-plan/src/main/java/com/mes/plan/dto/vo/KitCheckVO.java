package com.mes.plan.dto.vo;

import com.mes.plan.entity.PlanKitCheck;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 齐套分析记录 VO。
 */
@Data
public class KitCheckVO {
    private Long id;
    private String checkNo;
    private Long workorderId;
    private String woNo;
    private String result;
    private Integer shortCount;
    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     *
     * @param e 齐套分析实体
     * @return VO
     */
    public static KitCheckVO from(PlanKitCheck e) {
        KitCheckVO vo = new KitCheckVO();
        vo.setId(e.getId());
        vo.setCheckNo(e.getCheckNo());
        vo.setWorkorderId(e.getWorkorderId());
        vo.setWoNo(e.getWoNo());
        vo.setResult(e.getResult());
        vo.setShortCount(e.getShortCount());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}
