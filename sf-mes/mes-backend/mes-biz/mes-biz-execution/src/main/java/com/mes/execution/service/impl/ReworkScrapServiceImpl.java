package com.mes.execution.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.execution.dto.form.ReworkScrapForm;
import com.mes.execution.dto.vo.ReworkScrapVO;
import com.mes.execution.entity.ExeReworkScrap;
import com.mes.execution.mapper.ExeReworkScrapMapper;
import com.mes.execution.service.ReworkScrapService;
import com.mes.plan.dto.vo.WoStepVO;
import com.mes.plan.entity.PlanWorkorder;
import com.mes.plan.mapper.PlanWorkorderMapper;
import com.mes.plan.service.MesNoGenerator;
import com.mes.plan.service.WoStepService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 返工/报废单服务实现。
 */
@Service
@RequiredArgsConstructor
public class ReworkScrapServiceImpl implements ReworkScrapService {

    private final ExeReworkScrapMapper reworkScrapMapper;
    private final WoStepService woStepService;
    private final PlanWorkorderMapper workorderMapper;
    private final MesNoGenerator noGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ReworkScrapForm form) {
        boolean rework = "REWORK".equals(form.getType());
        if (!rework && !"SCRAP".equals(form.getType())) {
            throw new BusinessException("类型必须为 REWORK 或 SCRAP");
        }
        WoStepVO step = woStepService.getById(form.getWoStepId());
        PlanWorkorder wo = workorderMapper.selectById(step.getWorkorderId());
        if (wo == null) {
            throw new BusinessException("工单不存在: %d".formatted(step.getWorkorderId()));
        }
        ExeReworkScrap order = new ExeReworkScrap();
        order.setOrderNo(noGenerator.next(rework
                ? MesNoGenerator.PREFIX_REWORK : MesNoGenerator.PREFIX_SCRAP));
        order.setType(form.getType());
        order.setWorkorderId(wo.getId());
        order.setWoNo(wo.getWoNo());
        order.setWoStepId(step.getId());
        order.setOperationName(step.getOperationName());
        order.setSn(form.getSn());
        order.setQty(form.getQty());
        order.setReason(form.getReason());
        order.setQcRejectId(form.getQcRejectId());
        order.setStatus("OPEN");
        reworkScrapMapper.insert(order);
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, String handleRemark) {
        ExeReworkScrap order = reworkScrapMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "返工/报废单不存在: %d".formatted(id));
        }
        if (!"OPEN".equals(order.getStatus())) {
            throw new BusinessException("单据已处理，不可重复操作");
        }
        order.setStatus("CLOSED");
        order.setHandleRemark(handleRemark);
        order.setHandleTime(LocalDateTime.now());
        reworkScrapMapper.updateById(order);
    }

    @Override
    public PageResult<ReworkScrapVO> page(long pageNum, long pageSize, String keyword, String type, String status) {
        var wrapper = Wrappers.lambdaQuery(ExeReworkScrap.class)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(ExeReworkScrap::getOrderNo, keyword)
                        .or().like(ExeReworkScrap::getWoNo, keyword))
                .eq(StringUtils.hasText(type), ExeReworkScrap::getType, type)
                .eq(StringUtils.hasText(status), ExeReworkScrap::getStatus, status)
                .orderByDesc(ExeReworkScrap::getId);
        return PageUtils.of(reworkScrapMapper, wrapper, pageNum, pageSize, ReworkScrapVO::from);
    }
}
