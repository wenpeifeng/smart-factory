package com.mes.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 Mapper。
 * <p>
 * 继承 BaseMapper 即拥有 CRUD + 分页能力；用户-角色的联表查询见
 * {@link SysUserRoleMapper} 与对应 XML。
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}
