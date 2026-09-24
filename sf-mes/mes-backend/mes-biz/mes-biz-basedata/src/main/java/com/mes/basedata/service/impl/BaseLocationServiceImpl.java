package com.mes.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.dto.form.BaseLocationForm;
import com.mes.basedata.dto.vo.BaseLocationVO;
import com.mes.basedata.entity.BaseLocation;
import com.mes.basedata.entity.BaseWarehouse;
import com.mes.basedata.mapper.BaseLocationMapper;
import com.mes.basedata.mapper.BaseWarehouseMapper;
import com.mes.basedata.service.BaseLocationService;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库位 Service 实现（仓库下多库位）。
 */
@Service
@RequiredArgsConstructor
public class BaseLocationServiceImpl implements BaseLocationService {

    private final BaseLocationMapper locationMapper;
    private final BaseWarehouseMapper warehouseMapper;

    @Override
    public List<BaseLocationVO> listByWarehouse(Long warehouseId) {
        return locationMapper.selectList(Wrappers.lambdaQuery(BaseLocation.class)
                        .eq(BaseLocation::getWarehouseId, warehouseId)
                        .orderByAsc(BaseLocation::getCode))
                .stream().map(BaseLocationVO::from).toList();
    }

    @Override
    public Long create(BaseLocationForm form) {
        checkWarehouseExists(form.getWarehouseId());
        checkCodeUnique(form.getWarehouseId(), form.getCode().trim(), null);
        BaseLocation entity = new BaseLocation();
        applyForm(entity, form);
        locationMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, BaseLocationForm form) {
        BaseLocation entity = requireExists(id);
        if (!entity.getWarehouseId().equals(form.getWarehouseId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "库位不允许变更所属仓库");
        }
        checkCodeUnique(form.getWarehouseId(), form.getCode().trim(), id);
        applyForm(entity, form);
        locationMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        requireExists(id);
        // TODO 库存模块上线后：库位存在库存占用时抛 DATA_IN_USE
        locationMapper.deleteById(id);
    }

    /** 表单字段拷贝 */
    private void applyForm(BaseLocation entity, BaseLocationForm form) {
        entity.setWarehouseId(form.getWarehouseId());
        entity.setCode(form.getCode().trim());
        entity.setName(form.getName().trim());
        entity.setCapacity(form.getCapacity() == null ? BigDecimal.ZERO : form.getCapacity());
        entity.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        entity.setRemark(form.getRemark());
    }

    /** 仓库存在性校验 */
    private void checkWarehouseExists(Long warehouseId) {
        BaseWarehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "仓库不存在: %d".formatted(warehouseId));
        }
    }

    /** 库位编码在仓库内唯一（排除自身） */
    private void checkCodeUnique(Long warehouseId, String code, Long excludeId) {
        Long count = locationMapper.selectCount(Wrappers.lambdaQuery(BaseLocation.class)
                .eq(BaseLocation::getWarehouseId, warehouseId)
                .eq(BaseLocation::getCode, code)
                .ne(excludeId != null, BaseLocation::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_DATA, "库位编码在该仓库下已存在: %s".formatted(code));
        }
    }

    private BaseLocation requireExists(Long id) {
        BaseLocation entity = locationMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "库位不存在: %d".formatted(id));
        }
        return entity;
    }
}
