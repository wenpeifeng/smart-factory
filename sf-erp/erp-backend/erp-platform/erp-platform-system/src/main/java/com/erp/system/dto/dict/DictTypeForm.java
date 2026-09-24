package com.erp.system.dto.dict;

import com.erp.system.dto.Create;
import com.erp.system.dto.Update;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字典类型新增/修改表单。
 */
@Data
public class DictTypeForm {

    /** 类型 ID（修改时由路径参数注入，不做 JSR-303 约束） */
    private Long id;

    /** 字典名称 */
    @NotBlank(message = "字典名称不能为空", groups = {Create.class, Update.class})
    @Size(max = 50, message = "字典名称最长 50 字", groups = {Create.class, Update.class})
    private String name;

    /** 类型编码：字母开头，字母/数字/下划线（其他模块按此取数） */
    @NotBlank(message = "类型编码不能为空", groups = {Create.class, Update.class})
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,99}$", message = "编码须为字母开头的字母/数字/下划线，2-100 位",
            groups = {Create.class, Update.class})
    private String type;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注最长 200 字", groups = {Create.class, Update.class})
    private String remark;
}
