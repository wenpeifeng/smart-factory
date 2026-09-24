package com.mes.workflow.service;

import com.mes.common.result.PageResult;
import com.mes.workflow.dto.query.WfDefinitionQuery;
import com.mes.workflow.dto.form.WfDefinitionForm;
import com.mes.workflow.dto.vo.WfDefinitionVO;

/**
 * 流程定义服务 —— 定义的 CRUD 与启停用。
 * <p>
 * 节点随定义整体提交（覆盖式保存）；同一单据类型允许多条定义，
 * 发起时只匹配"启用"状态中最新的一条（见 WorkflowService#startFlow）。
 */
public interface WfDefinitionService {

    /**
     * 分页查询流程定义。
     *
     * @param query 查询条件（关键词/单据类型/状态）
     * @return 定义分页（含节点数统计）
     */
    PageResult<WfDefinitionVO> page(WfDefinitionQuery query);

    /**
     * 定义详情（含节点子表，按序号升序）。
     *
     * @param id 定义 ID
     * @return 定义 VO（带节点）
     * @throws com.mes.common.exception.BusinessException 定义不存在
     */
    WfDefinitionVO detail(Long id);

    /**
     * 新增流程定义（校验编码唯一/单据类型白名单/节点序号与条件表达式）。
     *
     * @param form 定义表单（含节点）
     * @return 新定义 ID
     */
    Long create(WfDefinitionForm form);

    /**
     * 修改流程定义（节点整体覆盖；不影响已发起实例——任务带节点快照）。
     *
     * @param id    定义 ID
     * @param form  定义表单（含节点）
     * @throws com.mes.common.exception.BusinessException 定义不存在或编码重复
     */
    void update(Long id, WfDefinitionForm form);

    /**
     * 删除流程定义（逻辑删，连带节点；存在审批中实例时禁止删除，应先停用）。
     *
     * @param id 定义 ID
     * @throws com.mes.common.exception.BusinessException 存在运行中实例
     */
    void delete(Long id);

    /**
     * 启用/停用流程定义。
     * <p>停用只影响新发起（startFlow 不再匹配），在途实例继续流转完。
     *
     * @param id     定义 ID
     * @param status 目标状态：1启用 0停用
     */
    void updateStatus(Long id, Integer status);
}
