package com.mes.system.dto.auth;

import com.mes.system.dto.user.UserVO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 登录成功响应：令牌 + 用户信息（不含口令）+ 租户/组织上下文。
 * <p>
 * v1.1 多租户改造：tenantId/orgId 为当前会话上下文（签入令牌），
 * orgs 为可切换组织列表（顶栏组织切换器数据源）。
 */
@Data
@AllArgsConstructor
public class LoginVO {

    /** JWT 访问令牌（携带 tenantId/orgId claims），后续请求放 Authorization: Bearer {token} */
    private String token;

    /** 登录用户信息 */
    private UserVO user;

    /** 当前租户 ID */
    private Long tenantId;

    /** 当前组织 ID（org_factory.id） */
    private Long orgId;

    /** 可切换组织列表 */
    private List<OrgVO> orgs;
}
