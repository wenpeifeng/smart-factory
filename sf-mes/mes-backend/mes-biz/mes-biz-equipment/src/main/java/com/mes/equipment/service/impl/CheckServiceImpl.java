package com.mes.equipment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.equipment.dto.form.CheckForm;
import com.mes.equipment.entity.EqpCheck;
import com.mes.equipment.entity.EqpDevice;
import com.mes.equipment.mapper.EqpCheckMapper;
import com.mes.equipment.mapper.EqpDeviceMapper;
import com.mes.equipment.service.CheckService;
import com.mes.framework.mybatis.PageUtils;
import com.mes.plan.service.MesNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 设备点检服务实现（NG 自动把设备置 REPAIR）。
 */
@Service
@RequiredArgsConstructor
public class CheckServiceImpl implements CheckService {

    private final EqpCheckMapper checkMapper;
    private final EqpDeviceMapper deviceMapper;
    private final MesNoGenerator noGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long record(CheckForm form) {
        EqpDevice device = deviceMapper.selectById(form.getDeviceId());
        if (device == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "设备不存在: %d".formatted(form.getDeviceId()));
        }
        boolean ng = "NG".equalsIgnoreCase(form.getResult());
        EqpCheck check = new EqpCheck();
        check.setCheckNo(noGenerator.next("DJ"));
        check.setDeviceId(device.getId());
        check.setCheckerName(form.getCheckerName());
        check.setResult(ng ? "NG" : "OK");
        check.setCheckTime(LocalDateTime.now());
        check.setRemark(form.getRemark());
        checkMapper.insert(check);
        if (ng && !"REPAIR".equals(device.getStatus())) {
            device.setStatus("REPAIR");
            deviceMapper.updateById(device);
        }
        return check.getId();
    }

    @Override
    public PageResult<EqpCheck> page(long pageNum, long pageSize, Long deviceId) {
        var wrapper = Wrappers.lambdaQuery(EqpCheck.class)
                .eq(deviceId != null, EqpCheck::getDeviceId, deviceId)
                .orderByDesc(EqpCheck::getId);
        return PageUtils.of(checkMapper, wrapper, pageNum, pageSize, e -> e);
    }
}
