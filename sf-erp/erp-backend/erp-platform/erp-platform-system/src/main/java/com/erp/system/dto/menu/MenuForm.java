package com.erp.system.dto.menu;

import com.erp.system.dto.Create;
import com.erp.system.dto.Update;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 菜单新增/修改表单（目录/菜单/按钮共用）。
 * <p>
 * 按类型做字段约束（服务端补充校验）：目录必须有 path 无 component/perms；
 * 菜单必须 path+component；按钮只需 perms。
 */
@Data
public class MenuForm {

    /** 菜单 ID（修改时由路径参数注入，不做 JSR-303 约束） */
    private Long id;

    /** 上级 ID，0 表示根 */
    @NotNull(message = "上级菜单不能为空", groups = {Create.class, Update.class})
    private Long parentId;

    /** 名称 */
    @NotBlank(message = "名称不能为空", groups = {Create.class, Update.class})
    @Size(max = 50, message = "名称最长 50 字", groups = {Create.class, Update.class})
    private String name;

    /** 类型：1-目录 2-菜单 3-按钮 */
    @NotNull(message = "类型不能为空", groups = Create.class)
    @Min(value = 1, message = "类型取值 1-3", groups = {Create.class, Update.class})
    @Max(value = 3, message = "类型取值 1-3", groups = {Create.class, Update.class})
    private Integer type;

    /** 路由地址（完整路径，不带斜杠） */
    @Size(max = 200, message = "路由地址最长 200 字符", groups = {Create.class, Update.class})
    private String path;

    /** 前端组件路径（相对 pages/） */
    @Size(max = 200, message = "组件路径最长 200 字符", groups = {Create.class, Update.class})
    private String component;

    /** 权限标识（按钮级） */
    @Size(max = 100, message = "权限标识最长 100 字符", groups = {Create.class, Update.class})
    private String perms;

    /** 图标名 */
    @Size(max = 50, message = "图标名最长 50 字符", groups = {Create.class, Update.class})
    private String icon;

    /** 显示顺序 */
    private Integer sort;

    /** 是否显示：1-显示 0-隐藏；null 按显示处理 */
    private Integer visible;

    /** 状态：1-启用 0-停用；null 按启用处理 */
    private Integer status;
}
