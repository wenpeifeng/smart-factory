package com.erp.basedata.dto.vo;

import com.erp.basedata.entity.BaseLocation;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库位响应 VO。
 * V421：+ zoneId（所属库区）+ zoneType（冗余展示）+ locType（库位用途）。
 */
@Data
public class BaseLocationVO {

    /** 主键 */
    private Long id;

    /** 所属仓库 ID */
    private Long warehouseId;

    /** 所属库区 ID（空=未分区，V421） */
    private Long zoneId;

    /** 库位用途：RECEIVE/STORAGE/PICKING/SHIP/DEFECT（V421） */
    private String locType;

    /** 库位编码（仓库内唯一） */
    private String code;

    /** 库位名称 */
    private String name;

    /** 容量 */
    private BigDecimal capacity;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 从实体转换。
     *
     * @param entity 库位实体
     * @return VO
     */
    public static BaseLocationVO from(BaseLocation entity) {
        BaseLocationVO vo = new BaseLocationVO();
        vo.setId(entity.getId());
        vo.setWarehouseId(entity.getWarehouseId());
        vo.setZoneId(entity.getZoneId());
        vo.setLocType(entity.getLocType());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setCapacity(entity.getCapacity());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
