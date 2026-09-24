package com.erp.workflow.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程定义 VO（列表/详情，详情带节点子表）。
 */
@Data
public class WfDefinitionVO {

    /** 定义 ID */
    private Long id;

    /** 流程编码 */
    private String flowCode;

    /** 流程名称 */
    private String flowName;

    /** 适用单据类型 */
    private String bizType;

    /** 状态：1启用 0停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 节点数（列表展示；详情时为节点子表） */
    private Long nodeCount;

    /** 节点子表（详情接口返回） */
    private List<WfNodeVO> nodes;

    /** 创建时间 */
    private LocalDateTime createTime;
}
