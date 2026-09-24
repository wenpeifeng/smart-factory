package com.erp.qc.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.qc.dto.form.QcTemplateForm;
import com.erp.qc.dto.query.QcTemplateQuery;
import com.erp.qc.dto.vo.QcTemplateVO;
import com.erp.qc.entity.QcTemplate;
import com.erp.qc.entity.QcTemplateItem;
import com.erp.qc.mapper.CrossModuleMapper;
import com.erp.qc.mapper.QcTemplateItemMapper;
import com.erp.qc.mapper.QcTemplateMapper;
import com.erp.qc.service.QcTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 检验模板 Service 实现。
 * <p>
 * 明细采用"整体覆盖式"保存（先逻辑删旧项目行再插入新行），与一期主子表单据口径一致；
 * 检验单明细是模板快照，模板修改/删除不追溯影响历史检验单。
 */
@Service
@RequiredArgsConstructor
public class QcTemplateServiceImpl implements QcTemplateService {

    private final QcTemplateMapper templateMapper;
    private final QcTemplateItemMapper templateItemMapper;
    private final CrossModuleMapper crossModuleMapper;

    @Override
    public PageResult<QcTemplateVO> page(QcTemplateQuery query) {
        var page = templateMapper.selectTemplatePage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public QcTemplateVO getDetail(Long id) {
        QcTemplate template = requireTemplate(id);
        QcTemplateVO vo = toVO(template);
        vo.setItems(templateItemMapper.selectByTemplateId(id).stream().map(this::toItemVO).toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(QcTemplateForm form) {
        requireCodeAvailable(form.getTemplateCode(), null);
        validateItems(form);
        QcTemplate template = new QcTemplate();
        template.setTemplateCode(form.getTemplateCode());
        template.setTemplateName(form.getTemplateName());
        template.setCategoryId(form.getCategoryId());
        template.setInspectType(form.getInspectType());
        template.setStatus(form.getStatus());
        template.setRemark(form.getRemark());
        templateMapper.insert(template);
        insertItems(template.getId(), form);
        return template.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, QcTemplateForm form) {
        requireTemplate(id);
        requireCodeAvailable(form.getTemplateCode(), id);
        validateItems(form);
        QcTemplate template = new QcTemplate();
        template.setId(id);
        template.setTemplateCode(form.getTemplateCode());
        template.setTemplateName(form.getTemplateName());
        template.setCategoryId(form.getCategoryId());
        template.setInspectType(form.getInspectType());
        template.setStatus(form.getStatus());
        template.setRemark(form.getRemark());
        templateMapper.updateById(template);
        // 整体覆盖：旧项目行逻辑删后重建
        templateItemMapper.delete(Wrappers.lambdaQuery(QcTemplateItem.class)
                .eq(QcTemplateItem::getTemplateId, id));
        insertItems(id, form);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireTemplate(id);
        templateMapper.deleteById(id);
        templateItemMapper.delete(Wrappers.lambdaQuery(QcTemplateItem.class)
                .eq(QcTemplateItem::getTemplateId, id));
    }

    /** 加载模板，不存在抛业务异常 */
    private QcTemplate requireTemplate(Long id) {
        QcTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw BusinessException.ofKey("erp.qc.template-not-exists", id);
        }
        return template;
    }

    /** 编码唯一性校验（excludeId 非空时排除自身） */
    private void requireCodeAvailable(String code, Long excludeId) {
        Long count = templateMapper.selectCount(Wrappers.lambdaQuery(QcTemplate.class)
                .eq(QcTemplate::getTemplateCode, code)
                .ne(excludeId != null, QcTemplate::getId, excludeId));
        if (count != null && count > 0) {
            throw BusinessException.ofKey("erp.qc.template-code-exists", code);
        }
    }

    /** 项目行校验：下限 ≤ 上限（两者均存在时） */
    private void validateItems(QcTemplateForm form) {
        List<QcTemplateForm.TemplateItemForm> items = form.getItems();
        for (int i = 0; i < items.size(); i++) {
            QcTemplateForm.TemplateItemForm item = items.get(i);
            if (item.getLowerLimit() != null && item.getUpperLimit() != null
                    && item.getLowerLimit().compareTo(item.getUpperLimit()) > 0) {
                throw BusinessException.ofKey("erp.qc.line-lower-over-upper", i + 1, item.getLowerLimit().toPlainString(), item.getUpperLimit().toPlainString());
            }
        }
    }

    /** 插入模板项目行 */
    private void insertItems(Long templateId, QcTemplateForm form) {
        form.getItems().forEach(line -> {
            QcTemplateItem item = new QcTemplateItem();
            item.setTemplateId(templateId);
            item.setItemName(line.getItemName());
            item.setStandardValue(line.getStandardValue());
            item.setLowerLimit(line.getLowerLimit());
            item.setUpperLimit(line.getUpperLimit());
            item.setInspectMethod(line.getInspectMethod());
            item.setSampleRate(line.getSampleRate());
            item.setSeq(line.getSeq() == null ? 0 : line.getSeq());
            item.setRemark(line.getRemark());
            templateItemMapper.insert(item);
        });
    }

    /** Entity → VO */
    private QcTemplateVO toVO(QcTemplate template) {
        QcTemplateVO vo = new QcTemplateVO();
        vo.setId(template.getId());
        vo.setTemplateCode(template.getTemplateCode());
        vo.setTemplateName(template.getTemplateName());
        vo.setCategoryId(template.getCategoryId());
        vo.setCategoryName(template.getCategoryId() == null
                ? null : crossModuleMapper.selectCategoryName(template.getCategoryId()));
        vo.setInspectType(template.getInspectType());
        vo.setStatus(template.getStatus());
        vo.setRemark(template.getRemark());
        vo.setCreateTime(template.getCreateTime());
        return vo;
    }

    /** 项目 Entity → VO */
    private QcTemplateVO.QcTemplateItemVO toItemVO(QcTemplateItem item) {
        QcTemplateVO.QcTemplateItemVO vo = new QcTemplateVO.QcTemplateItemVO();
        vo.setId(item.getId());
        vo.setItemName(item.getItemName());
        vo.setStandardValue(item.getStandardValue());
        vo.setLowerLimit(item.getLowerLimit());
        vo.setUpperLimit(item.getUpperLimit());
        vo.setInspectMethod(item.getInspectMethod());
        vo.setSampleRate(item.getSampleRate());
        vo.setSeq(item.getSeq());
        vo.setRemark(item.getRemark());
        return vo;
    }
}
