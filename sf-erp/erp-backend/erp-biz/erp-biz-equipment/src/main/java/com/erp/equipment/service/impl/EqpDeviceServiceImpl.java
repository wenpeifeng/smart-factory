package com.erp.equipment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.equipment.dto.form.DeviceForm;
import com.erp.equipment.dto.form.DeviceStatusForm;
import com.erp.equipment.dto.request.DeviceQuery;
import com.erp.equipment.dto.vo.DeviceVO;
import com.erp.equipment.entity.EqpDevice;
import com.erp.equipment.entity.EqpRepair;
import com.erp.equipment.mapper.EqpDeviceMapper;
import com.erp.equipment.mapper.EqpRepairMapper;
import com.erp.equipment.service.EqpDeviceService;
import com.erp.equipment.support.EqpBizNoGenerator;
import com.erp.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 设备台账服务实现。
 * <p>
 * 状态流转矩阵（changeStatus 校验）：1运行/2停机/3维修中 三态互通，4报废单向终态：
 * <pre>
 *          ┌──────┐  ┌──────┐  ┌──────┐
 *   运行 1 │ ←──→ │ 停机 2 │ ←──→ │ 维修中 3
 *          └──┬───┘  └──┬───┘  └──┬───┘
 *             └────────┼────────┘
 *                      ▼ (单向)
 *                  ┌──────┐
 *                  │ 报废 4 │ （终态，不可再流转）
 *                  └──────┘
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class EqpDeviceServiceImpl implements EqpDeviceService {

    private final EqpDeviceMapper deviceMapper;
    private final EqpRepairMapper repairMapper;
    private final EqpBizNoGenerator bizNoGenerator;

    @Override
    public PageResult<DeviceVO> page(DeviceQuery query) {
        return PageUtils.of(deviceMapper, Wrappers.lambdaQuery(EqpDevice.class)
                        .eq(query.getStatus() != null, EqpDevice::getStatus, query.getStatus())
                        .and(query.getKeyword() != null && !query.getKeyword().isBlank(),
                                w -> w.like(EqpDevice::getCode, query.getKeyword())
                                        .or().like(EqpDevice::getName, query.getKeyword())
                                        .or().like(EqpDevice::getModel, query.getKeyword()))
                        .orderByDesc(EqpDevice::getId),
                query.getPageNum(), query.getPageSize(), DeviceVO::from);
    }

    @Override
    public DeviceVO detail(Long id) {
        return DeviceVO.from(requireDevice(id));
    }

    @Override
    public Long create(DeviceForm form) {
        EqpDevice device = new EqpDevice();
        // 编号留空自动生成；手工指定时校验唯一
        if (form.getCode() == null || form.getCode().isBlank()) {
            device.setCode(bizNoGenerator.next("SB"));
        } else {
            device.setCode(form.getCode().trim());
            requireCodeUnused(device.getCode(), null);
        }
        applyForm(device, form);
        deviceMapper.insert(device);
        return device.getId();
    }

    @Override
    public void update(Long id, DeviceForm form) {
        EqpDevice device = requireDevice(id);
        if (device.getStatus() == EqpDevice.STATUS_SCRAPPED) {
            throw BusinessException.ofKey("erp.equipment.scrapped-no-modify", device.getCode());
        }
        // 编号以库内为准（防篡改唯一键）；状态走独立流转接口，修改台账不碰状态
        applyForm(device, form);
        device.setStatus(null);
        device.setId(id);
        deviceMapper.updateById(device);
    }

    @Override
    public void delete(Long id) {
        EqpDevice device = requireDevice(id);
        Long openRepairs = repairMapper.selectCount(Wrappers.lambdaQuery(EqpRepair.class)
                .eq(EqpRepair::getDeviceId, id)
                .in(EqpRepair::getStatus, EqpRepair.STATUS_PENDING, EqpRepair.STATUS_REPAIRING));
        if (openRepairs > 0) {
            throw BusinessException.ofKey("erp.equipment.has-open-repair-no-delete", device.getCode());
        }
        deviceMapper.deleteById(id);
    }

    @Override
    public void changeStatus(Long id, DeviceStatusForm form) {
        EqpDevice device = requireDevice(id);
        int from = device.getStatus();
        int to = form.getStatus();
        if (from == to) {
            throw BusinessException.ofKey("erp.equipment.already-in-status", device.getCode(), DeviceVO.statusLabelOf(from));
        }
        if (from == EqpDevice.STATUS_SCRAPPED) {
            throw BusinessException.ofKey("erp.equipment.scrapped-status-final", device.getCode());
        }
        if (!ALLOWED_TRANSITIONS.get(from).contains(to)) {
            throw BusinessException.ofKey("erp.equipment.transition-not-allowed", device.getCode(), DeviceVO.statusLabelOf(from), DeviceVO.statusLabelOf(to));
        }
        device.setStatus(to);
        deviceMapper.updateById(device);
    }

    /** 状态流转矩阵：非报废三态互通，报废单向终态（任何非报废状态可进） */
    private static final Map<Integer, Set<Integer>> ALLOWED_TRANSITIONS = Map.of(
            EqpDevice.STATUS_RUNNING, Set.of(EqpDevice.STATUS_STOPPED, EqpDevice.STATUS_REPAIRING,
                    EqpDevice.STATUS_SCRAPPED),
            EqpDevice.STATUS_STOPPED, Set.of(EqpDevice.STATUS_RUNNING, EqpDevice.STATUS_REPAIRING,
                    EqpDevice.STATUS_SCRAPPED),
            EqpDevice.STATUS_REPAIRING, Set.of(EqpDevice.STATUS_RUNNING, EqpDevice.STATUS_STOPPED,
                    EqpDevice.STATUS_SCRAPPED),
            EqpDevice.STATUS_SCRAPPED, Set.of());

    /** 表单 → 实体（编号/状态外的可编辑字段） */
    private void applyForm(EqpDevice device, DeviceForm form) {
        device.setName(form.getName());
        device.setModel(form.getModel());
        device.setManufacturer(form.getManufacturer());
        device.setPurchaseDate(form.getPurchaseDate());
        device.setOriginalValue(form.getOriginalValue());
        device.setWorkshop(form.getWorkshop());
        device.setStatus(form.getStatus());
        device.setResponsiblePerson(form.getResponsiblePerson());
        device.setCheckPeriodDays(form.getCheckPeriodDays());
        device.setRemark(form.getRemark());
    }

    /** 校验编号唯一（excludeId 用于修改场景，新增传 null） */
    private void requireCodeUnused(String code, Long excludeId) {
        Long count = deviceMapper.selectCount(Wrappers.lambdaQuery(EqpDevice.class)
                .eq(EqpDevice::getCode, code)
                .ne(excludeId != null, EqpDevice::getId, excludeId));
        if (count > 0) {
            throw BusinessException.ofKey("erp.equipment.equipment-code-exists", code);
        }
    }

    /** 取设备并在不存在时抛业务异常 */
    EqpDevice requireDevice(Long id) {
        EqpDevice device = deviceMapper.selectById(id);
        if (device == null) {
            throw BusinessException.ofKey("erp.equipment.equipment-not-exists", id);
        }
        return device;
    }
}
