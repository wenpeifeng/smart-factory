package com.erp.basedata.dto.vo;

import com.erp.basedata.entity.BaseZone;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库区响应 VO（V421）。
 */
@Data
public class BaseZoneVO {

    /** 主键 */
    private Long id;

    /** 所属仓库 ID */
    private Long warehouseId;

    /** 库区编码（仓库内唯一） */
    private String code;

    /** 库区名称 */
    private String name;

    /** 库区类型：RECEIVE/STORAGE/PICKING/SHIP/DEFECT */
    private String zoneType;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 从实体转换。
     *
     * @param entity 库区实体
     * @return VO
     */
    public static BaseZoneVO from(BaseZone entity) {
        BaseZoneVO vo = new BaseZoneVO();
        vo.setId(entity.getId());
        vo.setWarehouseId(entity.getWarehouseId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setZoneType(entity.getZoneType());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
