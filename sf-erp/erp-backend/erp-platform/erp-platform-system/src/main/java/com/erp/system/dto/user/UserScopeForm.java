package com.erp.system.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 用户数据范围/归属调整表单（身份体系收敛后唯一可改的用户属性组）。
 * <p>身份字段（账号/姓名/密码/工号/状态）归认证中心；这里只调整本系统内部授权：
 * 五档数据范围、所属部门（DEPT_ONLY 注入键）、所属组织（org 条件基准）。
 */
@Data
public class UserScopeForm {

    /** 数据范围：ALL/ORG_AND_CHILD/ORG_ONLY/DEPT_ONLY/SELF */
    @NotBlank(message = "数据范围不能为空")
    @Pattern(regexp = "ALL|ORG_AND_CHILD|ORG_ONLY|DEPT_ONLY|SELF",
            message = "数据范围仅支持 ALL/ORG_AND_CHILD/ORG_ONLY/DEPT_ONLY/SELF")
    private String dataScope;

    /** 所属组织 ID(组织类档位与 DEPT_ONLY 的组织基准) */
    private Long orgId;

    /** 所属部门 ID（DEPT_ONLY 注入 dept_id 条件；可空=按组织过滤） */
    private Long deptId;
}
