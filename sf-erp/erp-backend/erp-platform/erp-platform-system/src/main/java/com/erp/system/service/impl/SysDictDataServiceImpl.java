package com.erp.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import com.erp.system.dto.dict.DictDataForm;
import com.erp.system.dto.dict.DictDataQuery;
import com.erp.system.dto.dict.DictDataVO;
import com.erp.system.dto.dict.DictItemVO;
import com.erp.system.entity.SysDictData;
import com.erp.system.entity.SysDictType;
import com.erp.system.mapper.SysDictDataMapper;
import com.erp.system.mapper.SysDictTypeMapper;
import com.erp.system.service.SysDictDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典数据管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class SysDictDataServiceImpl implements SysDictDataService {

    private final SysDictDataMapper dictDataMapper;
    private final SysDictTypeMapper dictTypeMapper;

    @Override
    public PageResult<DictDataVO> page(DictDataQuery query) {
        var wrapper = Wrappers.lambdaQuery(SysDictData.class)
                .eq(query.getDictType() != null && !query.getDictType().isBlank(),
                        SysDictData::getDictType, query.getDictType())
                .and(query.getKeyword() != null && !query.getKeyword().isBlank(), w -> w
                        .like(SysDictData::getLabel, query.getKeyword().trim())
                        .or().like(SysDictData::getValue, query.getKeyword().trim()))
                .eq(query.getStatus() != null, SysDictData::getStatus, query.getStatus())
                .orderByAsc(SysDictData::getDictType)
                .orderByAsc(SysDictData::getSort)
                .orderByAsc(SysDictData::getId);
        return PageUtils.of(dictDataMapper, wrapper, query.getPageNum(), query.getPageSize(), DictDataVO::from);
    }

    @Override
    public Long create(DictDataForm form) {
        assertTypeExists(form.getDictType());
        assertLabelValueUnused(form, null);
        SysDictData data = new SysDictData();
        copyForm(form, data);
        dictDataMapper.insert(data);
        return data.getId();
    }

    @Override
    public void update(DictDataForm form) {
        requireData(form.getId());
        assertTypeExists(form.getDictType());
        assertLabelValueUnused(form, form.getId());
        SysDictData data = new SysDictData();
        copyForm(form, data);
        data.setId(form.getId());
        dictDataMapper.updateById(data);
    }

    @Override
    public void delete(Long id) {
        requireData(id);
        dictDataMapper.deleteById(id);
    }

    @Override
    public List<DictItemVO> listEnabledByType(String type) {
        // 类型不存在或已停用：返回空列表，调用方按"无选项"处理
        SysDictType dictType = dictTypeMapper.selectOne(Wrappers.lambdaQuery(SysDictType.class)
                .eq(SysDictType::getType, type));
        if (dictType == null || dictType.getStatus() == null || dictType.getStatus() != 1) {
            return List.of();
        }
        return dictDataMapper.selectList(Wrappers.lambdaQuery(SysDictData.class)
                        .eq(SysDictData::getDictType, type)
                        .eq(SysDictData::getStatus, 1)
                        .orderByAsc(SysDictData::getSort).orderByAsc(SysDictData::getId))
                .stream()
                .map(data -> new DictItemVO(data.getLabel(), data.getValue(), data.getSort()))
                .toList();
    }

    /** 同类型下 label 与 value 唯一（新增/修改共用） */
    private void assertLabelValueUnused(DictDataForm form, Long excludeId) {
        Long labelCount = dictDataMapper.selectCount(Wrappers.lambdaQuery(SysDictData.class)
                .eq(SysDictData::getDictType, form.getDictType())
                .eq(SysDictData::getLabel, form.getLabel())
                .ne(excludeId != null, SysDictData::getId, excludeId));
        if (labelCount != null && labelCount > 0) {
            throw BusinessException.ofKey("erp.system.dict-tag-exists", form.getDictType(), form.getLabel());
        }
        Long valueCount = dictDataMapper.selectCount(Wrappers.lambdaQuery(SysDictData.class)
                .eq(SysDictData::getDictType, form.getDictType())
                .eq(SysDictData::getValue, form.getValue())
                .ne(excludeId != null, SysDictData::getId, excludeId));
        if (valueCount != null && valueCount > 0) {
            throw BusinessException.ofKey("erp.system.dict-option-exists", form.getDictType(), form.getValue());
        }
    }

    private void assertTypeExists(String type) {
        Long count = dictTypeMapper.selectCount(Wrappers.lambdaQuery(SysDictType.class)
                .eq(SysDictType::getType, type));
        if (count == null || count == 0) {
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.system.dict-type-not-exists-by-code", type);
        }
    }

    private void copyForm(DictDataForm form, SysDictData data) {
        data.setDictType(form.getDictType());
        data.setLabel(form.getLabel());
        data.setValue(form.getValue());
        data.setSort(form.getSort() == null ? 0 : form.getSort());
        data.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        data.setRemark(form.getRemark());
    }

    private SysDictData requireData(Long id) {
        SysDictData data = dictDataMapper.selectById(id);
        if (data == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.system.dict-item-not-exists");
        }
        return data;
    }
}
