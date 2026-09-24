package com.erp.system.dto.role;

import com.erp.system.entity.SysRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色响应 VO。
 */
@Data
public class RoleVO {

    /** 角色 ID */
    private Long id;

    /** 角色名称 */
    private String name;

    /** 角色编码（super_admin 为内置超管） */
    private String code;

    /** 显示顺序 */
    private Integer sort;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     *
     * @param role 角色实体
     * @return VO
     */
    public static RoleVO from(SysRole role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setName(role.getName());
        vo.setCode(role.getCode());
        vo.setSort(role.getSort());
        vo.setStatus(role.getStatus());
        vo.setRemark(role.getRemark());
        vo.setCreateTime(role.getCreateTime());
        return vo;
    }
}
