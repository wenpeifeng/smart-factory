package com.mes.system.dto.role;

import lombok.Data;

import java.util.List;

/**
 * 角色分配菜单表单（整体覆盖语义）。
 */
@Data
public class RoleMenuForm {

    /** 菜单 ID 列表（含目录/菜单/按钮；空列表表示清空） */
    private List<Long> menuIds;
}
