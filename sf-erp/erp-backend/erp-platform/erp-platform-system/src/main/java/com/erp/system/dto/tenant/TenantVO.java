package com.erp.system.dto.tenant;

import com.erp.system.entity.SysTenant;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户响应 VO。
 */
@Data
public class TenantVO {

    /** 租户 ID（即 tenant_id 取值） */
    private Long id;

    /** 租户编码 */
    private String code;

    /** 租户名称 */
    private String name;

    /** 联系人 */
    private String contact;

    /** 联系电话 */
    private String phone;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 到期时间（null=永久） */
    private LocalDateTime expireTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     *
     * @param tenant 租户实体
     * @return VO
     */
    public static TenantVO from(SysTenant tenant) {
        TenantVO vo = new TenantVO();
        vo.setId(tenant.getId());
        vo.setCode(tenant.getCode());
        vo.setName(tenant.getName());
        vo.setContact(tenant.getContact());
        vo.setPhone(tenant.getPhone());
        vo.setStatus(tenant.getStatus());
        vo.setExpireTime(tenant.getExpireTime());
        vo.setRemark(tenant.getRemark());
        vo.setCreateTime(tenant.getCreateTime());
        return vo;
    }
}
