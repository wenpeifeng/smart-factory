package com.erp.equipment.dto.vo;

import com.erp.equipment.entity.EqpDevice;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备台账 VO（列表/详情共用）。
 */
@Data
public class DeviceVO {

    /** 设备 ID */
    private Long id;

    /** 设备编号 */
    private String code;

    /** 设备名称 */
    private String name;

    /** 规格型号 */
    private String model;

    /** 制造商 */
    private String manufacturer;

    /** 购置日期 */
    private LocalDate purchaseDate;

    /** 购置原值 */
    private BigDecimal originalValue;

    /** 所在车间 */
    private String workshop;

    /** 设备状态：1运行 2停机 3维修中 4报废 */
    private Integer status;

    /** 状态中文（前端 Tag 展示兜底） */
    private String statusLabel;

    /** 责任人 */
    private String responsiblePerson;

    /** 点检周期（天） */
    private Integer checkPeriodDays;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** Entity → VO */
    public static DeviceVO from(EqpDevice d) {
        DeviceVO vo = new DeviceVO();
        vo.setId(d.getId());
        vo.setCode(d.getCode());
        vo.setName(d.getName());
        vo.setModel(d.getModel());
        vo.setManufacturer(d.getManufacturer());
        vo.setPurchaseDate(d.getPurchaseDate());
        vo.setOriginalValue(d.getOriginalValue());
        vo.setWorkshop(d.getWorkshop());
        vo.setStatus(d.getStatus());
        vo.setStatusLabel(statusLabelOf(d.getStatus()));
        vo.setResponsiblePerson(d.getResponsiblePerson());
        vo.setCheckPeriodDays(d.getCheckPeriodDays());
        vo.setRemark(d.getRemark());
        vo.setCreateTime(d.getCreateTime());
        return vo;
    }

    /** 状态码 → 中文 */
    public static String statusLabelOf(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case EqpDevice.STATUS_RUNNING -> "运行";
            case EqpDevice.STATUS_STOPPED -> "停机";
            case EqpDevice.STATUS_REPAIRING -> "维修中";
            case EqpDevice.STATUS_SCRAPPED -> "报废";
            default -> "未知";
        };
    }
}
