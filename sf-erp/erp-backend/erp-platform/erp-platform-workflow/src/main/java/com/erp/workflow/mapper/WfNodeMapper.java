package com.erp.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.workflow.dto.vo.WfNodeVO;
import com.erp.workflow.entity.WfNode;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程节点 Mapper。
 */
public interface WfNodeMapper extends BaseMapper<WfNode> {

    /**
     * 查询定义下节点（按序号升序），带审批人展示名（角色名/用户姓名）。
     *
     * @param definitionId 流程定义 ID
     * @return 节点 VO 列表
     */
    List<WfNodeVO> selectNodesWithApproverName(@Param("definitionId") Long definitionId);
}
