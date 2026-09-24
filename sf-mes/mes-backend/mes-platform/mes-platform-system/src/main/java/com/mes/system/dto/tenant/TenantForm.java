package com.mes.system.dto.tenant;

import com.mes.system.dto.Create;
import com.mes.system.dto.Update;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户新增/修改表单（Entity 不直接作入参，防 mass assignment）。
 */
@Data
public class TenantForm {

    /** 租户 ID（修改时由路径参数注入） */
    private Long id;

    /** 租户编码：2-40 位字母/数字/下划线/短横 */
    @NotBlank(message = "租户编码不能为空", groups = {Create.class, Update.class})
    @Size(min = 2, max = 40, message = "租户编码长度须在 2-40 位之间", groups = {Create.class, Update.class})
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "租户编码仅支持字母、数字、下划线、短横", groups = {Create.class, Update.class})
    private String code;

    /** 租户名称 */
    @NotBlank(message = "租户名称不能为空", groups = {Create.class, Update.class})
    @Size(max = 100, message = "租户名称最长 100 字", groups = {Create.class, Update.class})
    private String name;

    /** 联系人（可选） */
    @Size(max = 50, message = "联系人最长 50 字", groups = {Create.class, Update.class})
    private String contact;

    /** 联系电话（可选） */
    @Size(max = 20, message = "联系电话最长 20 字符", groups = {Create.class, Update.class})
    private String phone;

    /** 状态：1-启用 0-停用；null 时按启用处理 */
    private Integer status;

    /** 到期时间（可选，null=永久） */
    private LocalDateTime expireTime;

    /** 备注 */
    @Size(max = 200, message = "备注最长 200 字", groups = {Create.class, Update.class})
    private String remark;
}
