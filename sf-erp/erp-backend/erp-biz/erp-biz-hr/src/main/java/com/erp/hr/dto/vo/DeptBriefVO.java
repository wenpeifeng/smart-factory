package com.erp.hr.dto.vo;

import lombok.Data;

/**
 * 部门简要信息（选择器，来源 sys_dept 只读）。
 */
@Data
public class DeptBriefVO {

    private Long id;

    private Long parentId;

    private String name;
}
