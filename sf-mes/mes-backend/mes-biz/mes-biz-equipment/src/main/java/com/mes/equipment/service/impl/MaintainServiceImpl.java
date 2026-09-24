package com.mes.equipment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.equipment.dto.form.MaintainForm;
import com.mes.equipment.entity.EqpDevice;
import com.mes.equipment.entity.EqpMaintain;
import com.mes.equipment.mapper.EqpDeviceMapper;
import com.mes.equipment.mapper.EqpMaintainMapper;
import com.mes.equipment.service.MaintainService;
import com.mes.framework.mybatis.PageUtils;
import com.mes.plan.service.MesNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 设备保养/维修服务实现（完成后设备恢复 IDLE）。
 */
@Service
@RequiredArgsConstructor
public class MaintainServiceImpl implements MaintainService {

    private final EqpMaintainMapper maintainMapper;
    private final EqpDeviceMapper deviceMapper;
    private final MesNoGenerator noGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(MaintainForm form) {
        if (deviceMapper.selectById(form.getDeviceId()) == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "设备不存在: %d".formatted(form.getDeviceId()));
        }
        if (!"PM".equals(form.getType()) && !"REPAIR".equals(form.getType())) {
            throw new BusinessException("类型必须为 PM 或 REPAIR");
        }
        EqpMaintain order = new EqpMaintain();
        order.setMaintainNo(noGenerator.next("BY"));
        order.setDeviceId(form.getDeviceId());
        order.setType(form.getType());
        order.setPlanDate(form.getPlanDate());
        order.setContent(form.getContent());
        order.setRemark(form.getRemark());
        order.setStatus("PLAN");
        maintainMapper.insert(order);
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long id, String maintainer, Integer durationMinutes) {
        EqpMaintain order = maintainMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "保养/维修单不存在: %d".formatted(id));
        }
        if (!"PLAN".equals(order.getStatus())) {
            throw new BusinessException("单据已完成，不可重复操作");
        }
        order.setStatus("DONE");
        order.setMaintainer(maintainer);
        order.setDurationMinutes(durationMinutes);
        order.setFinishTime(LocalDateTime.now());
        maintainMapper.updateById(order);
        EqpDevice device = deviceMapper.selectById(order.getDeviceId());
        if (device != null && "REPAIR".equals(device.getStatus())) {
            device.setStatus("IDLE");
            deviceMapper.updateById(device);
        }
    }

    @Override
    public PageResult<EqpMaintain> page(long pageNum, long pageSize, Long deviceId, String type, String status) {
        var wrapper = Wrappers.lambdaQuery(EqpMaintain.class)
                .eq(deviceId != null, EqpMaintain::getDeviceId, deviceId)
                .eq(StringUtils.hasText(type), EqpMaintain::getType, type)
                .eq(StringUtils.hasText(status), EqpMaintain::getStatus, status)
                .orderByDesc(EqpMaintain::getId);
        return PageUtils.of(maintainMapper, wrapper, pageNum, pageSize, e -> e);
    }
}
