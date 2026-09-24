package com.erp.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.basedata.entity.BaseZone;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库区 Mapper（表 base_zone，V421）。
 * <p>常规 CRUD 走 MyBatis-Plus BaseMapper；库区为租户隔离表
 * （不在 TenantConfig 共享白名单内，租户插件自动追加 tenant_id 条件）。
 */
@Mapper
public interface BaseZoneMapper extends BaseMapper<BaseZone> {
}
