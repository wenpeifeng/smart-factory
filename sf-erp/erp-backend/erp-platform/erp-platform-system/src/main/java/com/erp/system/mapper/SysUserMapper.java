package com.erp.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper。
 * <p>
 * 继承 BaseMapper 即拥有 CRUD + 分页能力（由 erp-server 启动类的
 * {@code @MapperScan("com.erp.**.mapper")} 统一扫描注册）。
 * 复杂 SQL 后续写入 resources/mapper/system/ 下同名 XML。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 登录用：按租户 + 账号定位用户。
     * <p>
     * {@code @InterceptorIgnore(tenantLine)}：登录发生在认证之前（无租户上下文），
     * 租户条件由本方法显式参数给出，禁用插件自动追加（否则恒按默认租户 1000 过滤）。
     *
     * @param username 登录账号
     * @param tenantId 租户 ID
     * @return 用户实体；账号/租户组合不存在返回 null
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND tenant_id = #{tenantId} AND deleted = 0")
    SysUser selectByUsernameAndTenant(@Param("username") String username, @Param("tenantId") Long tenantId);
}
