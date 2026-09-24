package com.erp.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.dto.form.BaseLocationForm;
import com.erp.basedata.dto.vo.BaseLocationVO;
import com.erp.basedata.entity.BaseLocation;
import com.erp.basedata.entity.BaseWarehouse;
import com.erp.basedata.mapper.BaseLocationMapper;
import com.erp.basedata.mapper.BaseWarehouseMapper;
import com.erp.basedata.mapper.BaseZoneMapper;
import com.erp.basedata.service.BaseLocationService;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
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
    private final BaseZoneMapper zoneMapper;

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
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.basedata.location-warehouse-immutable");
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

    /** 表单字段拷贝（V421：zoneId 归属校验 + locType 默认 STORAGE） */
    private void applyForm(BaseLocation entity, BaseLocationForm form) {
        if (form.getZoneId() != null) {
            checkZoneExists(form.getWarehouseId(), form.getZoneId());
        }
        entity.setWarehouseId(form.getWarehouseId());
        entity.setZoneId(form.getZoneId());
        entity.setLocType(form.getLocType() == null || form.getLocType().isBlank()
                ? "STORAGE" : form.getLocType());
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
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.warehouse-not-exists-id", warehouseId);
        }
    }

    /** 库区归属校验（V421）：库区必须存在且属于同一仓库，防止跨仓挂错 */
    private void checkZoneExists(Long warehouseId, Long zoneId) {
        Long count = zoneMapper.selectCount(Wrappers.lambdaQuery(com.erp.basedata.entity.BaseZone.class)
                .eq(com.erp.basedata.entity.BaseZone::getId, zoneId)
                .eq(com.erp.basedata.entity.BaseZone::getWarehouseId, warehouseId));
        if (count == null || count == 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.zone-not-in-warehouse", zoneId);
        }
    }

    /** 库位编码在仓库内唯一（排除自身） */
    private void checkCodeUnique(Long warehouseId, String code, Long excludeId) {
        Long count = locationMapper.selectCount(Wrappers.lambdaQuery(BaseLocation.class)
                .eq(BaseLocation::getWarehouseId, warehouseId)
                .eq(BaseLocation::getCode, code)
                .ne(excludeId != null, BaseLocation::getId, excludeId));
        if (count != null && count > 0) {
            throw BusinessException.ofKey(ErrorCode.DUPLICATE_DATA, "erp.basedata.location-code-exists", code);
        }
    }

    private BaseLocation requireExists(Long id) {
        BaseLocation entity = locationMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.location-not-exists", id);
        }
        return entity;
    }
}
