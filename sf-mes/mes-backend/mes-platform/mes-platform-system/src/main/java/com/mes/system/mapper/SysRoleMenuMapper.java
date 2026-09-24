package com.mes.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.system.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 角色-菜单关联 Mapper。
 * <p>
 * 物理删除 SQL 写在 resources/mapper/system/SysRoleMenu.xml。
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    /**
     * 物理删除某角色的全部菜单关联（重分配前调用）。
     *
     * @param roleId 角色 ID
     * @return 删除行数
     */
    int deletePhysicallyByRoleId(@Param("roleId") Long roleId);
}
