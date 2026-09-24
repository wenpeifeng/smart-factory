package com.erp.system.dto;

/**
 * JSR-303 校验分组标记：新增场景。
 * <p>
 * 用法：{@code @Validated(UserForm.Create.class)}，配合
 * {@code @NotBlank(groups = Create.class)} 实现同一 Form 在新增/修改下的差异校验
 * （如密码仅新增必填）。
 */
public interface Create {
}
