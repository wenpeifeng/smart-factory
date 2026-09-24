package com.erp.system.authcenter.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 认证中心同步专用用户 Mapper（authcenter 包内维护，不侵入既有 SysUserMapper）。
 * <p>查询发生在 SSO 登录（认证前，无租户上下文）与定时同步任务（无请求上下文），
 * 一律 {@code @InterceptorIgnore(tenantLine)} 禁用租户插件自动追加条件，
 * 匹配键 employee_no 全租户唯一（唯一索引 uk_employee_no）。
 */
@Mapper
public interface AuthCenterUserMapper extends BaseMapper<SysUser> {

    /**
     * 按工号定位用户（SSO 登录匹配 / 同步对账）。
     *
     * @param employeeNo 工号（sys_user.employee_no）
     * @return 用户实体；不存在返回 null
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_user WHERE employee_no = #{employeeNo} AND deleted = 0 LIMIT 1")
    SysUser selectByEmployeeNo(@Param("employeeNo") String employeeNo);

    /**
     * 全部同步候选本地用户（employee_no 非空的副本），孤儿对账用：
     * 中心全量列表之外的这些用户 = 已被回收分配（或中心侧删除），应本地禁用。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_user WHERE employee_no IS NOT NULL AND employee_no != '' AND deleted = 0")
    java.util.List<SysUser> selectAllSyncedUsers();
}
