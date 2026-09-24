package com.erp.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.dto.form.BaseWarehouseForm;
import com.erp.basedata.dto.vo.BaseWarehouseVO;
import com.erp.basedata.entity.BaseLocation;
import com.erp.basedata.entity.BaseWarehouse;
import com.erp.basedata.mapper.BaseLocationMapper;
import com.erp.basedata.mapper.BaseWarehouseMapper;
import com.erp.basedata.service.BaseWarehouseService;
import com.erp.basedata.service.BizCodeGenerator;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
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
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.basedata.warehouse-has-locations", locationCount);
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
            throw BusinessException.ofKey(ErrorCode.DUPLICATE_DATA, "erp.basedata.warehouse-code-exists", code);
        }
    }

    private BaseWarehouse requireExists(Long id) {
        BaseWarehouse entity = warehouseMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.warehouse-not-exists-id", id);
        }
        return entity;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
