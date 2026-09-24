package com.mes.system.dto.dict;

import com.mes.system.dto.Create;
import com.mes.system.dto.Update;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字典数据（字典项）新增/修改表单。
 */
@Data
public class DictDataForm {

    /** 字典项 ID（修改时由路径参数注入，不做 JSR-303 约束） */
    private Long id;

    /** 所属字典类型编码 */
    @NotBlank(message = "所属类型不能为空", groups = {Create.class, Update.class})
    @Size(max = 100, message = "类型编码最长 100 字符", groups = {Create.class, Update.class})
    private String dictType;

    /** 选项标签（展示） */
    @NotBlank(message = "标签不能为空", groups = {Create.class, Update.class})
    @Size(max = 50, message = "标签最长 50 字", groups = {Create.class, Update.class})
    private String label;

    /** 选项值（存储） */
    @NotBlank(message = "选项值不能为空", groups = {Create.class, Update.class})
    @Size(max = 50, message = "选项值最长 50 字", groups = {Create.class, Update.class})
    private String value;

    /** 显示顺序 */
    private Integer sort;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注最长 200 字", groups = {Create.class, Update.class})
    private String remark;
}
