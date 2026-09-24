package com.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.entity.SysDept;
import org.apache.ibatis.annotations.Mapper;

/**
 * 部门 Mapper。树形组装在 Service 层完成（内存组树，部门数据量小）。
 */
@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {
}
