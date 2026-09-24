package com.mes.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.system.entity.SysTenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户 Mapper。
 * <p>
 * 注意：sys_tenant 在租户插件共享白名单内（TenantConfig），查询不追加租户条件 ——
 * 平台管理门户跨租户维护租户档案；业务模块禁止引用本表。
 */
@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenant> {
}
