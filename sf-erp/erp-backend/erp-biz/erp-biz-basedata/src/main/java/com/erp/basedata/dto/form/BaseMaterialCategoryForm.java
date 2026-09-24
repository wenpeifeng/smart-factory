package com.erp.basedata.dto.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 物料分类新增/修改表单（树形节点）。
 */
@Data
public class BaseMaterialCategoryForm {

    /** 父分类 ID，0=根节点 */
    @NotNull(message = "父分类不能为空")
    @Min(value = 0, message = "父分类 ID 非法")
    private Long parentId;

    /** 分类编码 */
    @NotBlank(message = "分类编码不能为空")
    @Size(max = 30, message = "分类编码长度不能超过30")
    private String code;

    /** 分类名称 */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过50")
    private String name;

    /** 排序号 */
    @NotNull(message = "排序号不能为空")
    @Min(value = 0, message = "排序号不能为负数")
    @Max(value = 9999, message = "排序号不能超过9999")
    private Integer sort;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
