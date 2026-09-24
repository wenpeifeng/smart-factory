package com.erp.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.basedata.entity.BaseMaterialCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 物料分类 Mapper。
 * <p>树查询一次取全表在内存组装（分类量级小，无需递归 SQL）。
 */
@Mapper
public interface BaseMaterialCategoryMapper extends BaseMapper<BaseMaterialCategory> {
}
