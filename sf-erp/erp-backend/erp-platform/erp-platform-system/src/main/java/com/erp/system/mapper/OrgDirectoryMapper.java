package com.erp.system.mapper;

import com.erp.system.dto.auth.OrgTreeNode;
import com.erp.system.dto.auth.OrgVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 组织目录 Mapper（登录上下文 / 数据权限支撑）。
 * <p>
 * 说明：组织档案表 org_factory 属于 erp-biz-org 模块，但认证/组织上下文位于
 * erp-platform-system（依赖方向 system ← org，无法反向调用其 Service），
 * 故此处以只读 SQL 直查 —— 组织树（org_org）落地后应下沉为 framework SPI 的正式实现，
 * 届时删除本类（集成期妥协，已在设计文档登记）。
 * org_factory 在租户插件共享白名单内，本查询不追加租户条件。
 */
@Mapper
public interface OrgDirectoryMapper {

    /**
     * 查询全部启用组织（工厂）。
     *
     * @return 组织列表（id/code/name）
     */
    @Select("SELECT id, code, name FROM org_factory WHERE deleted = 0 AND status = 1 ORDER BY id")
    List<OrgVO> selectEnabledOrgs();

    /**
     * 查询全部组织树节点（含停用组织，仅滤逻辑删除）。
     * <p>
     * 停用组织的存量业务数据仍归属该组织，上级组织 ORG_AND_CHILD 档应可见，
     * 故递归收集不按 status 过滤（组织列表本身的启停过滤在 selectEnabledOrgs）。
     *
     * @return 节点列表（id/parent_id，V1041 起有父子层级）
     */
    @Select("SELECT id, parent_id AS parentId FROM org_factory WHERE deleted = 0")
    List<OrgTreeNode> selectTreeNodes();
}
