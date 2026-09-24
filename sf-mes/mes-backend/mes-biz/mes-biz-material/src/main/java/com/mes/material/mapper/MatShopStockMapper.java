package com.mes.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.material.entity.MatShopStock;
import org.apache.ibatis.annotations.Mapper;

/**
 * MatShopStock Mapper（简单条件走 MP，无 XML）。
 */
@Mapper
public interface MatShopStockMapper extends BaseMapper<MatShopStock> {
}
