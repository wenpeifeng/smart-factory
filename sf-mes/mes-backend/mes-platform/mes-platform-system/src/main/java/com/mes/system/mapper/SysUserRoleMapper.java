package com.mes.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户-角色关联 Mapper。
 * <p>
 * 物理删除 SQL 写在 resources/mapper/system/SysUserRoleMapper.xml：
 * 重分配属于"整体覆盖"语义，逻辑删除会累积垃圾行。
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 物理删除某用户的全部角色关联（重分配前调用）。
     *
     * @param userId 用户 ID
     * @return 删除行数
     */
    int deletePhysicallyByUserId(@Param("userId") Long userId);
}
