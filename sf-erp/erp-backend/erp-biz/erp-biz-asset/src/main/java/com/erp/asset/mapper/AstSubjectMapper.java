package com.erp.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.asset.entity.FinSubject;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会计科目只读 Mapper（fin_subject，本模块按协作约定只读）。
 */
@Mapper
public interface AstSubjectMapper extends BaseMapper<FinSubject> {
}
