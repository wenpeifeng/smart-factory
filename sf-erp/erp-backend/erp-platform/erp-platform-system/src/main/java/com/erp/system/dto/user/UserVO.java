package com.erp.system.dto.user;

import com.erp.system.entity.SysUser;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户响应 VO。
 * <p>
 * 安全约束：{@code 永不包含 password 字段}（BCrypt 密文也不允许出参）。
 */
@Data
public class UserVO {

    /** 用户 ID */
    private Long id;

    /** 工号（认证中心匹配键；本地降级账号为空） */
    private String employeeNo;

    /** 登录账号 */
    private String username;

    /** 显示姓名 */
    private String nickname;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 所属部门 ID */
    private Long deptId;

    /** 所属部门名称（列表/详情聚合展示） */
    private String deptName;

    /** 租户 ID（v1.1 多租户） */
    private Long tenantId;

    /** 主归属组织 ID（org_factory.id） */
    private Long orgId;

    /** 数据范围：ALL/ORG_AND_CHILD/ORG_ONLY/DEPT_ONLY/SELF */
    private String dataScope;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 已分配角色 ID 列表（回显勾选） */
    private List<Long> roleIds;

    /** 已分配角色名称列表（展示） */
    private List<String> roleNames;

    /**
     * 实体转 VO（不含口令与角色信息，角色由 Service 聚合补充）。
     *
     * @param user 用户实体
     * @return VO
     */
    public static UserVO from(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setEmployeeNo(user.getEmployeeNo());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setDeptId(user.getDeptId());
        vo.setTenantId(user.getTenantId());
        vo.setOrgId(user.getOrgId());
        vo.setDataScope(user.getDataScope());
        vo.setStatus(user.getStatus());
        vo.setRemark(user.getRemark());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
