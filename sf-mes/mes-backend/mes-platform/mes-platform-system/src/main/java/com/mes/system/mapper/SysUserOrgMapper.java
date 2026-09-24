package com.mes.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.system.entity.SysUserOrg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-组织绑定 Mapper（V1042）。
 * <p>
 * 物理删除 SQL 写在 resources/mapper/system/SysUserOrgMapper.xml：
 * 绑定维护为"整体覆盖"语义，逻辑删除会在 uk(user_id, org_id) 上撞键。
 */
@Mapper
public interface SysUserOrgMapper extends BaseMapper<SysUserOrg> {

    /**
     * 查询用户绑定的组织 ID 列表（登录可切换组织过滤用）。
     *
     * @param userId 用户 ID
     * @return 组织 ID 列表（未绑定为空）
     */
    @Select("SELECT org_id FROM sys_user_org WHERE user_id = #{userId}")
    List<Long> selectOrgIdsByUserId(@Param("userId") Long userId);

    /**
     * 物理删除某用户的全部组织绑定（重分配前调用）。
     *
     * @param userId 用户 ID
     * @return 删除行数
     */
    int deletePhysicallyByUserId(@Param("userId") Long userId);
}
