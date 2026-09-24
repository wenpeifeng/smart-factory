package com.erp.system.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 组织 VO（登录上下文里的可切换组织列表项）。
 * <p>
 * 当前组织模型为 org_factory（工厂/组织档案）；org_org 集团树落地后
 * 该 VO 扩展 type/parentId 字段即可，前端契约不变。
 */
@Data
@AllArgsConstructor
public class OrgVO {

    /** 组织 ID（org_factory.id，业务表 org_id 取值） */
    private Long id;

    /** 组织编码 */
    private String code;

    /** 组织名称 */
    private String name;
}
