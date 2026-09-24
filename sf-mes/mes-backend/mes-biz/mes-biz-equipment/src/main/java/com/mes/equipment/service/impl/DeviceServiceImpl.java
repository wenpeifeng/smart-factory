package com.mes.equipment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.entity.BaseWorkcenter;
import com.mes.basedata.mapper.BaseWorkcenterMapper;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.equipment.dto.form.DeviceForm;
import com.mes.equipment.dto.vo.DeviceVO;
import com.mes.equipment.entity.EqpDevice;
import com.mes.equipment.mapper.EqpDeviceMapper;
import com.mes.equipment.service.DeviceService;
import com.mes.plan.service.MesNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 设备台账服务实现。
 */
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private static final List<String> STATUSES = List.of("RUNNING", "IDLE", "DOWN", "REPAIR");

    private final EqpDeviceMapper deviceMapper;
    private final BaseWorkcenterMapper workcenterMapper;
    private final MesNoGenerator noGenerator;

    @Override
    public PageResult<DeviceVO> page(long pageNum, long pageSize, String keyword, Long workcenterId, String status) {
        var wrapper = Wrappers.lambdaQuery(EqpDevice.class)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(EqpDevice::getCode, keyword)
                        .or().like(EqpDevice::getName, keyword)
                        .or().like(EqpDevice::getModel, keyword))
                .eq(workcenterId != null, EqpDevice::getWorkcenterId, workcenterId)
                .eq(StringUtils.hasText(status), EqpDevice::getStatus, status)
                .orderByDesc(EqpDevice::getId);
        PageResult<DeviceVO> page = com.mes.framework.mybatis.PageUtils.of(
                deviceMapper, wrapper, pageNum, pageSize, DeviceVO::from);
        if (!page.getRecords().isEmpty()) {
            Map<Long, String> wcNames = workcenterNames(page.getRecords());
            page.getRecords().forEach(vo -> {
                if (vo.getWorkcenterId() != null) {
                    vo.setWorkcenterName(wcNames.get(vo.getWorkcenterId()));
                }
            });
        }
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(DeviceForm form) {
        EqpDevice device = new EqpDevice();
        applyForm(device, form);
        device.setCode(noGenerator.next("SB"));
        device.setStatus("IDLE");
        deviceMapper.insert(device);
        return device.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DeviceForm form) {
        EqpDevice device = requireDevice(id);
        applyForm(device, form);
        deviceMapper.updateById(device);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        deviceMapper.deleteById(requireDevice(id).getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status) {
        if (!STATUSES.contains(status)) {
            throw new BusinessException("非法设备状态: %s", status);
        }
        EqpDevice device = requireDevice(id);
        device.setStatus(status);
        deviceMapper.updateById(device);
    }

    private void applyForm(EqpDevice device, DeviceForm form) {
        device.setName(form.getName());
        device.setModel(form.getModel());
        device.setWorkcenterId(form.getWorkcenterId());
        device.setLocation(form.getLocation());
        device.setBuyDate(form.getBuyDate());
        device.setRemark(form.getRemark());
    }

    private Map<Long, String> workcenterNames(List<DeviceVO> records) {
        List<Long> ids = records.stream().map(DeviceVO::getWorkcenterId)
                .filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return workcenterMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(BaseWorkcenter::getId, BaseWorkcenter::getName));
    }

    private EqpDevice requireDevice(Long id) {
        EqpDevice device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "设备不存在: %d".formatted(id));
        }
        return device;
    }
}
