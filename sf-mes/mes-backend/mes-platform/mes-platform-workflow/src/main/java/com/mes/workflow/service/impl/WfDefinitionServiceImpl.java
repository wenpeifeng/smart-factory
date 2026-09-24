package com.mes.workflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.workflow.constant.WfConstants;
import com.mes.workflow.dto.form.WfDefinitionForm;
import com.mes.workflow.dto.query.WfDefinitionQuery;
import com.mes.workflow.dto.vo.WfDefinitionVO;
import com.mes.workflow.entity.WfDefinition;
import com.mes.workflow.entity.WfInstance;
import com.mes.workflow.entity.WfNode;
import com.mes.workflow.mapper.WfDefinitionMapper;
import com.mes.workflow.mapper.WfInstanceMapper;
import com.mes.workflow.mapper.WfNodeMapper;
import com.mes.workflow.service.WfDefinitionService;
import com.mes.workflow.support.ConditionEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 流程定义服务实现。
 * <p>
 * 节点随定义整体提交（覆盖式保存：逻辑删旧节点 + 插新节点），
 * 与采购单明细同一口径，避免差量合并复杂度。
 * 已发起实例不受修改影响（任务携带节点序号/名称快照）。
 */
@Service
@RequiredArgsConstructor
public class WfDefinitionServiceImpl implements WfDefinitionService {

    private final WfDefinitionMapper definitionMapper;
    private final WfNodeMapper nodeMapper;
    private final WfInstanceMapper instanceMapper;

    @Override
    public PageResult<WfDefinitionVO> page(WfDefinitionQuery query) {
        var page = definitionMapper.selectDefinitionPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                query.getKeyword(), query.getBizType(), query.getStatus());
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public WfDefinitionVO detail(Long id) {
        WfDefinition definition = requireDefinition(id);
        WfDefinitionVO vo = toVo(definition);
        vo.setNodes(nodeMapper.selectNodesWithApproverName(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(WfDefinitionForm form) {
        validateForm(null, form);
        WfDefinition definition = new WfDefinition();
        definition.setFlowCode(form.getFlowCode());
        definition.setFlowName(form.getFlowName());
        definition.setBizType(form.getBizType());
        definition.setStatus(form.getStatus());
        definition.setRemark(form.getRemark());
        definitionMapper.insert(definition);
        insertNodes(definition.getId(), form.getNodes());
        return definition.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, WfDefinitionForm form) {
        requireDefinition(id);
        validateForm(id, form);
        WfDefinition definition = new WfDefinition();
        definition.setId(id);
        definition.setFlowCode(form.getFlowCode());
        definition.setFlowName(form.getFlowName());
        definition.setBizType(form.getBizType());
        definition.setStatus(form.getStatus());
        definition.setRemark(form.getRemark());
        definitionMapper.updateById(definition);
        // 节点整体覆盖：逻辑删旧 + 插新（@TableLogic 自动转 UPDATE deleted=1）
        nodeMapper.delete(Wrappers.lambdaQuery(WfNode.class).eq(WfNode::getDefinitionId, id));
        insertNodes(id, form.getNodes());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireDefinition(id);
        // 运行中实例仍引用节点定义，删除会让在途流程失去上下文 —— 先停用等实例走完
        Long running = instanceMapper.selectCount(Wrappers.lambdaQuery(WfInstance.class)
                .eq(WfInstance::getDefinitionId, id)
                .eq(WfInstance::getStatus, WfInstance.STATUS_RUNNING));
        if (running != null && running > 0) {
            throw new BusinessException("该流程存在 %d 个审批中实例，不允许删除，请先停用", running);
        }
        definitionMapper.deleteById(id);
        nodeMapper.delete(Wrappers.lambdaQuery(WfNode.class).eq(WfNode::getDefinitionId, id));
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != WfDefinition.STATUS_ENABLED && status != WfDefinition.STATUS_DISABLED)) {
            throw new BusinessException("状态值仅支持 1启用 / 0停用");
        }
        requireDefinition(id);
        WfDefinition definition = new WfDefinition();
        definition.setId(id);
        definition.setStatus(status);
        definitionMapper.updateById(definition);
    }

    /** 实体转 VO（不含节点子表，由 detail 单独填充） */
    private WfDefinitionVO toVo(WfDefinition definition) {
        WfDefinitionVO vo = new WfDefinitionVO();
        vo.setId(definition.getId());
        vo.setFlowCode(definition.getFlowCode());
        vo.setFlowName(definition.getFlowName());
        vo.setBizType(definition.getBizType());
        vo.setStatus(definition.getStatus());
        vo.setRemark(definition.getRemark());
        vo.setCreateTime(definition.getCreateTime());
        return vo;
    }

    /** 插入节点子表 */
    private void insertNodes(Long definitionId, List<WfDefinitionForm.NodeForm> nodes) {
        for (WfDefinitionForm.NodeForm nodeForm : nodes) {
            WfNode node = new WfNode();
            node.setDefinitionId(definitionId);
            node.setNodeOrder(nodeForm.getNodeOrder());
            node.setNodeName(nodeForm.getNodeName());
            node.setApproverType(nodeForm.getApproverType());
            node.setApproverId(nodeForm.getApproverId());
            node.setConditionExpr(nodeForm.getConditionExpr());
            nodeMapper.insert(node);
        }
    }

    /** 表单校验：类型白名单/编码唯一/节点序号唯一/条件表达式语法 */
    private void validateForm(Long excludeId, WfDefinitionForm form) {
        if (!WfConstants.BIZ_TYPES.contains(form.getBizType())) {
            throw new BusinessException("单据类型[%s]不在支持范围: %s", form.getBizType(), WfConstants.BIZ_TYPES);
        }
        var codeCheck = Wrappers.lambdaQuery(WfDefinition.class)
                .eq(WfDefinition::getFlowCode, form.getFlowCode());
        if (excludeId != null) {
            codeCheck.ne(WfDefinition::getId, excludeId);
        }
        if (definitionMapper.selectCount(codeCheck) > 0) {
            throw new BusinessException("流程编码[%s]已存在", form.getFlowCode());
        }
        // 节点序号唯一 + 条件表达式语法（写错早暴露，不至于运行时把单据卡死）
        Set<Integer> orders = new HashSet<>();
        for (WfDefinitionForm.NodeForm node : form.getNodes()) {
            if (!orders.add(node.getNodeOrder())) {
                throw new BusinessException("节点序号[%d]重复，节点序号必须唯一", node.getNodeOrder());
            }
            ConditionEvaluator.validate(node.getConditionExpr());
        }
        if (form.getStatus() == null
                || (form.getStatus() != WfDefinition.STATUS_ENABLED
                && form.getStatus() != WfDefinition.STATUS_DISABLED)) {
            throw new BusinessException("状态值仅支持 1启用 / 0停用");
        }
    }

    /** 按ID取定义，不存在即抛业务异常 */
    private WfDefinition requireDefinition(Long id) {
        WfDefinition definition = definitionMapper.selectById(id);
        if (definition == null) {
            throw new BusinessException("流程定义[%d]不存在", id);
        }
        return definition;
    }
}
