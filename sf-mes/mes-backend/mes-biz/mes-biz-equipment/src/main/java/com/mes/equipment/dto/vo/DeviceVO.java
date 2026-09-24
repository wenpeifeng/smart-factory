package com.mes.equipment.dto.vo;

import com.mes.equipment.entity.EqpDevice;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备台账 VO。
 */
@Data
public class DeviceVO {
    private Long id;
    private String code;
    private String name;
    private String model;
    private Long workcenterId;
    private String workcenterName;
    private String status;
    private String location;
    private LocalDate buyDate;
    private String remark;
    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     *
     * @param e 设备实体
     * @return VO
     */
    public static DeviceVO from(EqpDevice e) {
        DeviceVO vo = new DeviceVO();
        vo.setId(e.getId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setModel(e.getModel());
        vo.setWorkcenterId(e.getWorkcenterId());
        vo.setStatus(e.getStatus());
        vo.setLocation(e.getLocation());
        vo.setBuyDate(e.getBuyDate());
        vo.setRemark(e.getRemark());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}
