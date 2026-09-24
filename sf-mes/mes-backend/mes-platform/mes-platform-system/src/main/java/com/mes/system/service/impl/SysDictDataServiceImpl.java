package com.mes.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.system.dto.dict.DictDataForm;
import com.mes.system.dto.dict.DictDataQuery;
import com.mes.system.dto.dict.DictDataVO;
import com.mes.system.dto.dict.DictItemVO;
import com.mes.system.entity.SysDictData;
import com.mes.system.entity.SysDictType;
import com.mes.system.mapper.SysDictDataMapper;
import com.mes.system.mapper.SysDictTypeMapper;
import com.mes.system.service.SysDictDataService;
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
            throw new BusinessException("类型[%s]下标签[%s]已存在", form.getDictType(), form.getLabel());
        }
        Long valueCount = dictDataMapper.selectCount(Wrappers.lambdaQuery(SysDictData.class)
                .eq(SysDictData::getDictType, form.getDictType())
                .eq(SysDictData::getValue, form.getValue())
                .ne(excludeId != null, SysDictData::getId, excludeId));
        if (valueCount != null && valueCount > 0) {
            throw new BusinessException("类型[%s]下选项值[%s]已存在", form.getDictType(), form.getValue());
        }
    }

    private void assertTypeExists(String type) {
        Long count = dictTypeMapper.selectCount(Wrappers.lambdaQuery(SysDictType.class)
                .eq(SysDictType::getType, type));
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "字典类型[%s]不存在".formatted(type));
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
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "字典项不存在或已被删除");
        }
        return data;
    }
}
