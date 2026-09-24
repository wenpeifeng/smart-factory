package com.mes.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.system.dto.dict.DictTypeForm;
import com.mes.system.dto.dict.DictTypeQuery;
import com.mes.system.dto.dict.DictTypeVO;
import com.mes.system.entity.SysDictData;
import com.mes.system.entity.SysDictType;
import com.mes.system.mapper.SysDictDataMapper;
import com.mes.system.mapper.SysDictTypeMapper;
import com.mes.system.service.SysDictTypeService;
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
            throw new BusinessException("类型编码[%s]已存在", form.getType());
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
            throw new BusinessException(ErrorCode.DATA_IN_USE, "该类型下仍有字典项，请先删除字典项");
        }
        dictTypeMapper.deleteById(id);
    }

    private void assertTypeUnused(String type, Long excludeId) {
        Long count = dictTypeMapper.selectCount(Wrappers.lambdaQuery(SysDictType.class)
                .eq(SysDictType::getType, type)
                .ne(excludeId != null, SysDictType::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("类型编码[%s]已存在", type);
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
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "字典类型不存在或已被删除");
        }
        return dictType;
    }
}
