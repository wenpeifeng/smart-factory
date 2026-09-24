package com.erp.system.dto.user;

import lombok.Data;

import java.util.List;

/**
 * 用户绑定组织表单（整体覆盖语义，PUT /system/users/{id}/orgs）。
 * <p>
 * 空列表 = 清空绑定：该用户登录回退为可见全部启用组织（兼容存量/超管）。
 */
@Data
public class UserOrgForm {

    /** 组织 ID 列表（org_factory.id，须为启用组织；空列表清空绑定） */
    private List<Long> orgIds;
}
