package com.erp.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import com.erp.system.dto.dict.DictTypeForm;
import com.erp.system.dto.dict.DictTypeQuery;
import com.erp.system.dto.dict.DictTypeVO;
import com.erp.system.entity.SysDictData;
import com.erp.system.entity.SysDictType;
import com.erp.system.mapper.SysDictDataMapper;
import com.erp.system.mapper.SysDictTypeMapper;
import com.erp.system.service.SysDictTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字典类型管理服务实现。
 * <p>
 * 类型编码被字典数据表逻辑引用（dict_type 字符串），修改编码时级联更新字典数据，
 * 保证 GET /dict/{type} 关联不脱钩。
 */
@Service
@RequiredArgsConstructor
public class SysDictTypeServiceImpl implements SysDictTypeService {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;

    @Override
    public PageResult<DictTypeVO> page(DictTypeQuery query) {
        var wrapper = Wrappers.lambdaQuery(SysDictType.class)
                .and(query.getKeyword() != null && !query.getKeyword().isBlank(), w -> w
                        .like(SysDictType::getName, query.getKeyword().trim())
                        .or().like(SysDictType::getType, query.getKeyword().trim()))
                .eq(query.getStatus() != null, SysDictType::getStatus, query.getStatus())
                .orderByAsc(SysDictType::getId);
        return PageUtils.of(dictTypeMapper, wrapper, query.getPageNum(), query.getPageSize(), DictTypeVO::from);
    }

    @Override
    public Long create(DictTypeForm form) {
        assertTypeUnused(form.getType(), null);
        SysDictType dictType = new SysDictType();
        copyForm(form, dictType);
        try {
            dictTypeMapper.insert(dictType);
        } catch (DuplicateKeyException e) {
            throw BusinessException.ofKey("erp.system.dict-type-code-exists", form.getType());
        }
        return dictType.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DictTypeForm form) {
        SysDictType exists = requireType(form.getId());
        assertTypeUnused(form.getType(), form.getId());
        SysDictType dictType = new SysDictType();
        copyForm(form, dictType);
        dictType.setId(form.getId());
        dictTypeMapper.updateById(dictType);
        // 编码被修改：级联同步字典数据的关联编码
        if (!exists.getType().equals(form.getType())) {
            SysDictData patch = new SysDictData();
            patch.setDictType(form.getType());
            dictDataMapper.update(patch, Wrappers.lambdaUpdate(SysDictData.class)
                    .eq(SysDictData::getDictType, exists.getType()));
        }
    }

    @Override
    public void delete(Long id) {
        SysDictType dictType = requireType(id);
        Long dataCount = dictDataMapper.selectCount(Wrappers.lambdaQuery(SysDictData.class)
                .eq(SysDictData::getDictType, dictType.getType()));
        if (dataCount != null && dataCount > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.system.dict-type-has-items");
        }
        dictTypeMapper.deleteById(id);
    }

    private void assertTypeUnused(String type, Long excludeId) {
        Long count = dictTypeMapper.selectCount(Wrappers.lambdaQuery(SysDictType.class)
                .eq(SysDictType::getType, type)
                .ne(excludeId != null, SysDictType::getId, excludeId));
        if (count != null && count > 0) {
            throw BusinessException.ofKey("erp.system.dict-type-code-exists", type);
        }
    }

    private void copyForm(DictTypeForm form, SysDictType dictType) {
        dictType.setName(form.getName());
        dictType.setType(form.getType());
        dictType.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        dictType.setRemark(form.getRemark());
    }

    private SysDictType requireType(Long id) {
        SysDictType dictType = dictTypeMapper.selectById(id);
        if (dictType == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.system.dict-type-not-exists");
        }
        return dictType;
    }
}
