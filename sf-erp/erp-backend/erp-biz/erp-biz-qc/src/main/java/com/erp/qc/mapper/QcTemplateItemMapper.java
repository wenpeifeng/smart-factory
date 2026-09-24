package com.erp.qc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.qc.entity.QcTemplateItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 检验模板明细（检验项目）Mapper。
 */
public interface QcTemplateItemMapper extends BaseMapper<QcTemplateItem> {

    /**
     * 按模板 ID 查检验项目（按 seq、id 排序）。
     */
    @Select("SELECT * FROM qc_template_item WHERE template_id = #{templateId} AND deleted = 0 "
            + "ORDER BY seq ASC, id ASC")
    List<QcTemplateItem> selectByTemplateId(@Param("templateId") Long templateId);
}
