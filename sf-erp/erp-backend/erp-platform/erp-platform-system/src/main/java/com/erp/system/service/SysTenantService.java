package com.erp.system.service;

import com.erp.common.result.PageResult;
import com.erp.system.dto.tenant.TenantForm;
import com.erp.system.dto.tenant.TenantQuery;
import com.erp.system.dto.tenant.TenantVO;

import java.util.List;

/**
 * 租户管理服务（管理门户）：分页/详情/新增/修改/启停。
 * <p>
 * 约束：默认租户 1000 不可删除/停用/改码（存量数据归属，删之即失隔离锚点）。
 */
public interface SysTenantService {

    /**
     * 分页查询租户。
     *
     * @param query 查询条件（keyword/status + 分页排序）
     * @return 租户分页
     */
    PageResult<TenantVO> page(TenantQuery query);

    /**
     * 全量启用租户（登录页租户选择器数据源）。
     *
     * @return 启用租户列表
     */
    List<TenantVO> listEnabled();

    /**
     * 租户详情。
     *
     * @param id 租户 ID
     * @return 租户 VO
     */
    TenantVO getDetail(Long id);

    /**
     * 新增租户。
     *
     * @param form 租户表单
     * @return 租户 ID
     */
    Long create(TenantForm form);

    /**
     * 修改租户（编码变更同步校验唯一）。
     *
     * @param form 租户表单（含 ID）
     */
    void update(TenantForm form);

    /**
     * 启停租户（停用后该租户用户不可登录）。
     *
     * @param id     租户 ID
     * @param status 目标状态：1-启用 0-停用
     */
    void updateStatus(Long id, Integer status);
}
