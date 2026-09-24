package com.erp.org.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工厂档案 VO（含归属仓库数，供列表/详情展示）。
 */
@Data
public class FactoryVO {

    private Long id;

    /** 工厂编码 */
    private String code;

    /** 工厂名称 */
    private String name;

    /** 工厂地址 */
    private String address;

    /** 负责人 */
    private String manager;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 归属仓库数（含停用仓库） */
    private Long warehouseCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 从实体构建（不含仓库数，调用方按需补齐）。
     */
    public static FactoryVO from(com.erp.org.entity.OrgFactory f) {
        FactoryVO vo = new FactoryVO();
        vo.setId(f.getId());
        vo.setCode(f.getCode());
        vo.setName(f.getName());
        vo.setAddress(f.getAddress());
        vo.setManager(f.getManager());
        vo.setStatus(f.getStatus());
        vo.setRemark(f.getRemark());
        vo.setCreateTime(f.getCreateTime());
        return vo;
    }
}
