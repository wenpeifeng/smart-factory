package com.mes.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.dto.form.BaseWarehouseForm;
import com.mes.basedata.dto.vo.BaseWarehouseVO;
import com.mes.basedata.entity.BaseLocation;
import com.mes.basedata.entity.BaseWarehouse;
import com.mes.basedata.mapper.BaseLocationMapper;
import com.mes.basedata.mapper.BaseWarehouseMapper;
import com.mes.basedata.service.BaseWarehouseService;
import com.mes.basedata.service.BizCodeGenerator;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 仓库 Service 实现。
 */
@Service
@RequiredArgsConstructor
public class BaseWarehouseServiceImpl implements BaseWarehouseService {

    private final BaseWarehouseMapper warehouseMapper;
    private final BaseLocationMapper locationMapper;
    private final BizCodeGenerator codeGenerator;

    @Override
    public PageResult<BaseWarehouseVO> page(long pageNum, long pageSize, String keyword) {
        var wrapper = Wrappers.lambdaQuery(BaseWarehouse.class)
                .and(notBlank(keyword), w -> w.like(BaseWarehouse::getCode, keyword)
                        .or().like(BaseWarehouse::getName, keyword)
                        .or().like(BaseWarehouse::getManager, keyword))
                .orderByDesc(BaseWarehouse::getId);
        return PageUtils.of(warehouseMapper, wrapper, pageNum, pageSize, BaseWarehouseVO::from);
    }

    @Override
    public BaseWarehouseVO getById(Long id) {
        return BaseWarehouseVO.from(requireExists(id));
    }

    @Override
    public Long create(BaseWarehouseForm form) {
        BaseWarehouse entity = new BaseWarehouse();
        applyForm(entity, form, true);
        warehouseMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, BaseWarehouseForm form) {
        BaseWarehouse entity = requireExists(id);
        applyForm(entity, form, false);
        warehouseMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        requireExists(id);
        // 存在库位禁删：库位承载库存，删仓库必须先清空库位
        Long locationCount = locationMapper.selectCount(Wrappers.lambdaQuery(BaseLocation.class)
                .eq(BaseLocation::getWarehouseId, id));
        if (locationCount != null && locationCount > 0) {
            throw new BusinessException(ErrorCode.DATA_IN_USE,
                    "仓库下存在 %d 个库位，请先删除库位".formatted(locationCount));
        }
        // TODO 库存/单据模块上线后：存在库存或被单据引用时抛 DATA_IN_USE
        warehouseMapper.deleteById(id);
    }

    @Override
    public List<BaseWarehouse> getByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return warehouseMapper.selectBatchIds(ids);
    }

    @Override
    public List<BaseWarehouseVO> listEnabled() {
        return warehouseMapper.selectList(Wrappers.lambdaQuery(BaseWarehouse.class)
                        .eq(BaseWarehouse::getStatus, 1)
                        .orderByAsc(BaseWarehouse::getCode))
                .stream().map(BaseWarehouseVO::from).toList();
    }

    /** 表单字段拷贝；create 模式下编码留空自动生成 */
    private void applyForm(BaseWarehouse entity, BaseWarehouseForm form, boolean isCreate) {
        String code = form.getCode() == null ? "" : form.getCode().trim();
        if (isCreate) {
            entity.setCode(code.isEmpty() ? codeGenerator.nextCode(BizCodeGenerator.PREFIX_WAREHOUSE) : code);
        } else if (!code.isEmpty()) {
            entity.setCode(code);
        }
        checkCodeUnique(entity.getCode(), entity.getId());
        entity.setName(form.getName().trim());
        entity.setManager(form.getManager());
        entity.setAddress(form.getAddress());
        entity.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        entity.setRemark(form.getRemark());
    }

    /** 仓库编码唯一性校验（排除自身） */
    private void checkCodeUnique(String code, Long excludeId) {
        Long count = warehouseMapper.selectCount(Wrappers.lambdaQuery(BaseWarehouse.class)
                .eq(BaseWarehouse::getCode, code)
                .ne(excludeId != null, BaseWarehouse::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_DATA, "仓库编码已存在: %s".formatted(code));
        }
    }

    private BaseWarehouse requireExists(Long id) {
        BaseWarehouse entity = warehouseMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "仓库不存在: %d".formatted(id));
        }
        return entity;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
