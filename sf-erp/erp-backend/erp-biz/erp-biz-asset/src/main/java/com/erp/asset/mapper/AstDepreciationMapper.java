package com.erp.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.asset.entity.AstDepreciation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 折旧计提期间 Mapper（MP 通用 CRUD）。
 */
@Mapper
public interface AstDepreciationMapper extends BaseMapper<AstDepreciation> {
}
