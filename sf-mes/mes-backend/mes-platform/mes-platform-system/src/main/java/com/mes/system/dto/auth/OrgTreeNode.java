package com.mes.system.dto.auth;

import lombok.Data;

/**
 * 组织树节点 DTO（OrgTreeProvider 递归收集子组织的输入）。
 * <p>
 * 仅取 id/parentId 两列（组织档案为小表，全量载入内存递归）；
 * org_org 集团树落地后由其实现替换，本结构不变。
 */
@Data
public class OrgTreeNode {

    /** 组织 ID（org_factory.id） */
    private Long id;

    /** 上级组织 ID（NULL=根；V1041 新增列） */
    private Long parentId;
}
