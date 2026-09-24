package com.erp.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.dto.form.BaseUnitForm;
import com.erp.basedata.dto.vo.BaseUnitVO;
import com.erp.basedata.entity.BaseMaterial;
import com.erp.basedata.entity.BaseUnit;
import com.erp.basedata.mapper.BaseMaterialMapper;
import com.erp.basedata.mapper.BaseUnitMapper;
import com.erp.basedata.service.BaseUnitService;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 计量单位 Service 实现。
 */
@Service
@RequiredArgsConstructor
public class BaseUnitServiceImpl implements BaseUnitService {

    private final BaseUnitMapper unitMapper;
    private final BaseMaterialMapper materialMapper;

    @Override
    public PageResult<BaseUnitVO> page(long pageNum, long pageSize, String keyword) {
        var wrapper = Wrappers.lambdaQuery(BaseUnit.class)
                .and(notBlank(keyword), w -> w.like(BaseUnit::getName, keyword)
                        .or().like(BaseUnit::getGroupCode, keyword))
                .orderByAsc(BaseUnit::getGroupCode)
                .orderByDesc(BaseUnit::getId);
        return PageUtils.of(unitMapper, wrapper, pageNum, pageSize, BaseUnitVO::from);
    }

    @Override
    public List<BaseUnitVO> listEnabled() {
        return unitMapper.selectList(Wrappers.lambdaQuery(BaseUnit.class)
                        .eq(BaseUnit::getStatus, 1)
                        .orderByAsc(BaseUnit::getGroupCode))
                .stream().map(BaseUnitVO::from).toList();
    }

    @Override
    public BaseUnitVO getById(Long id) {
        return BaseUnitVO.from(requireExists(id));
    }

    @Override
    public Long create(BaseUnitForm form) {
        BaseUnit entity = new BaseUnit();
        applyForm(entity, form);
        unitMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, BaseUnitForm form) {
        BaseUnit entity = requireExists(id);
        applyForm(entity, form);
        unitMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        requireExists(id);
        // 被物料引用的单位禁删（换算基础被破坏会导致历史单据无法换算）
        Long refCount = materialMapper.selectCount(Wrappers.lambdaQuery(BaseMaterial.class)
                .eq(BaseMaterial::getUnitId, id));
        if (refCount != null && refCount > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.basedata.uom-in-use", refCount);
        }
        unitMapper.deleteById(id);
    }

    /** 表单字段拷贝到实体（新增/修改共用） */
    private void applyForm(BaseUnit entity, BaseUnitForm form) {
        entity.setGroupCode(form.getGroupCode().trim());
        entity.setName(form.getName().trim());
        entity.setRate(form.getRate());
        entity.setIsBase(form.getIsBase());
        entity.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        entity.setRemark(form.getRemark());
    }

    /** 按 ID 取实体，不存在抛 DATA_NOT_EXISTS */
    private BaseUnit requireExists(Long id) {
        BaseUnit entity = unitMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.uom-not-exists", id);
        }
        return entity;
    }

    /** 字符串非空判断（keyword 场景高频，收口一处） */
    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
