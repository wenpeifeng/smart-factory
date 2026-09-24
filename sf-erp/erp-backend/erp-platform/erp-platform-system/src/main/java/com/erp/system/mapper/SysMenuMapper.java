package com.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜单 Mapper。
 * <p>
 * 除标准 CRUD 外提供按用户聚合菜单的联表查询（user_role -> role_menu -> menu），
 * SQL 写在 resources/mapper/system/SysMenuMapper.xml。
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 查询用户可见（所属角色启用且菜单启用）的菜单列表。
     *
     * @param userId 用户 ID
     * @param types  菜单类型过滤（1 目录 / 2 菜单 / 3 按钮），null 表示不过滤
     * @return 菜单实体列表（未排序，由调用方按 sort 组装树）
     */
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId, @Param("types") List<Integer> types);
}
