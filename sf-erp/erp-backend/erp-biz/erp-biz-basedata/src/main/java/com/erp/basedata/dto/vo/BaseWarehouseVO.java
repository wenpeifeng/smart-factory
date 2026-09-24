package com.erp.basedata.dto.vo;

import com.erp.basedata.entity.BaseWarehouse;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 仓库响应 VO。
 */
@Data
public class BaseWarehouseVO {

    /** 主键 */
    private Long id;

    /** 仓库编码 */
    private String code;

    /** 仓库名称 */
    private String name;

    /** 负责人 */
    private String manager;

    /** 地址 */
    private String address;

    /** 是否启用：1-是 0-否 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 从实体转换。
     *
     * @param entity 仓库实体
     * @return VO
     */
    public static BaseWarehouseVO from(BaseWarehouse entity) {
        BaseWarehouseVO vo = new BaseWarehouseVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setManager(entity.getManager());
        vo.setAddress(entity.getAddress());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
