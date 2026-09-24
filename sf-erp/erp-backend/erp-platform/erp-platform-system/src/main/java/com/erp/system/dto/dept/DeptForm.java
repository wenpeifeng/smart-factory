package com.erp.system.dto.dept;

import com.erp.system.dto.Create;
import com.erp.system.dto.Update;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部门新增/修改表单。
 */
@Data
public class DeptForm {

    /** 部门 ID（修改时由路径参数注入，不做 JSR-303 约束） */
    private Long id;

    /** 上级部门 ID，0 表示根 */
    @NotNull(message = "上级部门不能为空", groups = {Create.class, Update.class})
    private Long parentId;

    /** 部门名称 */
    @NotBlank(message = "部门名称不能为空", groups = {Create.class, Update.class})
    @Size(max = 50, message = "部门名称最长 50 字", groups = {Create.class, Update.class})
    private String name;

    /** 显示顺序 */
    private Integer sort;

    /** 负责人 */
    @Size(max = 30, message = "负责人最长 30 字", groups = {Create.class, Update.class})
    private String leader;

    /** 联系电话 */
    @Pattern(regexp = "^$|^[0-9+\\-()]{5,20}$", message = "联系电话格式不正确", groups = {Create.class, Update.class})
    private String phone;

    /** 状态：1-启用 0-停用；null 时服务端按启用处理 */
    private Integer status;
}
