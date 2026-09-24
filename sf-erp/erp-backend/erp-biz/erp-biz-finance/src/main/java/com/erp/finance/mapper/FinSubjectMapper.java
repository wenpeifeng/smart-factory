package com.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.finance.entity.FinSubject;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会计科目 Mapper。
 * <p>
 * 继承 BaseMapper 即拥有 CRUD + 分页能力（由 erp-server 启动类的
 * {@code @MapperScan("com.erp.**.mapper")} 统一扫描注册）。
 */
@Mapper
public interface FinSubjectMapper extends BaseMapper<FinSubject> {
}
