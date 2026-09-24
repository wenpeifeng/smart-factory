package com.mes.basedata.dto.vo;

import com.mes.basedata.entity.BaseWorkcenter;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工作中心 VO。
 */
@Data
public class BaseWorkcenterVO {
    private Long id;
    private String code;
    private String name;
    private String workshop;
    private BigDecimal capacityHoursPerDay;
    private Integer equipmentCount;
    private Long teamId;
    private String status;

    /**
     * 实体转 VO。
     *
     * @param e 工作中心实体
     * @return VO
     */
    public static BaseWorkcenterVO from(BaseWorkcenter e) {
        BaseWorkcenterVO vo = new BaseWorkcenterVO();
        vo.setId(e.getId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setWorkshop(e.getWorkshop());
        vo.setCapacityHoursPerDay(e.getCapacityHoursPerDay());
        vo.setEquipmentCount(e.getEquipmentCount());
        vo.setTeamId(e.getTeamId());
        vo.setStatus(e.getStatus());
        return vo;
    }
}
