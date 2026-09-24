package com.mes.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.workflow.dto.vo.WfDefinitionVO;
import com.mes.workflow.entity.WfDefinition;
import org.apache.ibatis.annotations.Param;

/**
 * 流程定义 Mapper。
 */
public interface WfDefinitionMapper extends BaseMapper<WfDefinition> {

    /**
     * 分页查询流程定义（带节点数统计）。
     *
     * @param page    分页参数（MP 自动注入）
     * @param keyword 编码/名称模糊
     * @param bizType 单据类型
     * @param status  状态
     * @return 定义 VO 分页
     */
    IPage<WfDefinitionVO> selectDefinitionPage(Page<WfDefinitionVO> page,
                                               @Param("keyword") String keyword,
                                               @Param("bizType") String bizType,
                                               @Param("status") Integer status);
}
