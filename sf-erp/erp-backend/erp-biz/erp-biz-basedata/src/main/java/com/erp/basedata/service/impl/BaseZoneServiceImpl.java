package com.erp.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.dto.form.BaseZoneForm;
import com.erp.basedata.dto.vo.BaseLocationVO;
import com.erp.basedata.dto.vo.BaseZoneVO;
import com.erp.basedata.dto.vo.LocationTreeNodeVO;
import com.erp.basedata.entity.BaseWarehouse;
import com.erp.basedata.entity.BaseZone;
import com.erp.basedata.mapper.BaseWarehouseMapper;
import com.erp.basedata.mapper.BaseZoneMapper;
import com.erp.basedata.service.BaseLocationService;
import com.erp.basedata.service.BaseZoneService;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库区 Service 实现（V421）。
 * <p>
 * 树形接口 locationTree 在服务层拼装（库区一次查全 + 库位一次查全后内存分组），
 * 未挂库区的库位统一收进"未分区"兜底组，保证前端树不漏库位。
 */
@Service
@RequiredArgsConstructor
public class BaseZoneServiceImpl implements BaseZoneService {

    /** 未分区兜底组的虚拟库区 ID */
    private static final long UNZONED_ID = 0L;

    private final BaseZoneMapper zoneMapper;
    private final BaseWarehouseMapper warehouseMapper;
    private final BaseLocationService locationService;

    @Override
    public List<BaseZoneVO> listByWarehouse(Long warehouseId) {
        return zoneMapper.selectList(Wrappers.lambdaQuery(BaseZone.class)
                        .eq(BaseZone::getWarehouseId, warehouseId)
                        .orderByAsc(BaseZone::getZoneType)
                        .orderByAsc(BaseZone::getCode))
                .stream().map(BaseZoneVO::from).toList();
    }

    @Override
    public List<LocationTreeNodeVO> locationTree(Long warehouseId) {
        List<BaseZoneVO> zones = listByWarehouse(warehouseId);
        List<BaseLocationVO> locations = locationService.listByWarehouse(warehouseId);

        // LinkedHashMap 保持库区顺序；未分区组固定排最后
        Map<Long, LocationTreeNodeVO> groups = new LinkedHashMap<>();
        for (BaseZoneVO zone : zones) {
            LocationTreeNodeVO node = new LocationTreeNodeVO();
            node.setZoneId(zone.getId());
            node.setZoneCode(zone.getCode());
            node.setZoneName(zone.getName());
            node.setZoneType(zone.getZoneType());
            groups.put(zone.getId(), node);
        }
        LocationTreeNodeVO unzoned = new LocationTreeNodeVO();
        unzoned.setZoneId(UNZONED_ID);
        unzoned.setZoneCode("-");
        unzoned.setZoneName("未分区");
        unzoned.setZoneType("STORAGE");
        groups.put(UNZONED_ID, unzoned);

        for (BaseLocationVO loc : locations) {
            LocationTreeNodeVO node = loc.getZoneId() == null ? unzoned
                    : groups.getOrDefault(loc.getZoneId(), unzoned);
            node.getLocations().add(loc);
        }
        return groups.values().stream().filter(n -> !n.getLocations().isEmpty()).toList();
    }

    @Override
    public Long create(BaseZoneForm form) {
        checkWarehouseExists(form.getWarehouseId());
        checkCodeUnique(form.getWarehouseId(), form.getCode().trim(), null);
        BaseZone entity = new BaseZone();
        applyForm(entity, form);
        zoneMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, BaseZoneForm form) {
        BaseZone entity = requireExists(id);
        if (!entity.getWarehouseId().equals(form.getWarehouseId())) {
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.basedata.zone-warehouse-immutable");
        }
        checkCodeUnique(form.getWarehouseId(), form.getCode().trim(), id);
        applyForm(entity, form);
        zoneMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        requireExists(id);
        // 库区下仍有库位时拒绝删除，避免库位悬空（库位归零需先改挂其他库区）
        Long locCount = locationService.listByWarehouse(requireExists(id).getWarehouseId())
                .stream().filter(l -> id.equals(l.getZoneId())).count();
        if (locCount != null && locCount > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.basedata.zone-has-locations");
        }
        zoneMapper.deleteById(id);
    }

    /** 表单字段拷贝 */
    private void applyForm(BaseZone entity, BaseZoneForm form) {
        entity.setWarehouseId(form.getWarehouseId());
        entity.setCode(form.getCode().trim());
        entity.setName(form.getName().trim());
        entity.setZoneType(form.getZoneType());
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

    /** 库区编码在仓库内唯一（排除自身） */
    private void checkCodeUnique(Long warehouseId, String code, Long excludeId) {
        Long count = zoneMapper.selectCount(Wrappers.lambdaQuery(BaseZone.class)
                .eq(BaseZone::getWarehouseId, warehouseId)
                .eq(BaseZone::getCode, code)
                .ne(excludeId != null, BaseZone::getId, excludeId));
        if (count != null && count > 0) {
            throw BusinessException.ofKey(ErrorCode.DUPLICATE_DATA, "erp.basedata.zone-code-exists", code);
        }
    }

    private BaseZone requireExists(Long id) {
        BaseZone entity = zoneMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.zone-not-exists", id);
        }
        return entity;
    }
}
