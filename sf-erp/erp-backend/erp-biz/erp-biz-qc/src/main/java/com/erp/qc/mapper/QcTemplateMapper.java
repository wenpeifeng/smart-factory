package com.erp.qc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.qc.dto.query.QcTemplateQuery;
import com.erp.qc.dto.vo.QcTemplateVO;
import com.erp.qc.entity.QcTemplate;
import org.apache.ibatis.annotations.Param;

/**
 * 检验模板 Mapper。
 */
public interface QcTemplateMapper extends BaseMapper<QcTemplate> {

    /**
     * 分页查询模板（JOIN 物料分类带出名称）。
     */
    IPage<QcTemplateVO> selectTemplatePage(Page<QcTemplateVO> page, @Param("q") QcTemplateQuery q);
}
