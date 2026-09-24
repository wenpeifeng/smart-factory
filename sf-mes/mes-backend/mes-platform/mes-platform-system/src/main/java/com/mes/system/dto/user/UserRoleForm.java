package com.mes.system.dto.user;

import lombok.Data;

import java.util.List;

/**
 * 用户分配角色表单（整体覆盖语义）。
 */
@Data
public class UserRoleForm {

    /** 角色 ID 列表（空列表表示清空角色） */
    private List<Long> roleIds;
}
