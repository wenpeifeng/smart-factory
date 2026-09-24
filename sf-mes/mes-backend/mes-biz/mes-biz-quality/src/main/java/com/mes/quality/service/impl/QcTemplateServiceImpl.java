package com.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.quality.dto.form.QcTemplateForm;
import com.mes.quality.dto.vo.QcTemplateVO;
import com.mes.quality.entity.QcTemplate;
import com.mes.quality.entity.QcTemplateItem;
import com.mes.quality.mapper.QcTemplateItemMapper;
import com.mes.quality.mapper.QcTemplateMapper;
import com.mes.quality.service.QcTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 检验模板服务实现（模板为共享数据，项目行随头全量维护）。
 */
@Service
@RequiredArgsConstructor
public class QcTemplateServiceImpl implements QcTemplateService {

    private final QcTemplateMapper templateMapper;
    private final QcTemplateItemMapper templateItemMapper;

    @Override
    public PageResult<QcTemplateVO> page(long pageNum, long pageSize, String keyword, String inspectType) {
        var wrapper = Wrappers.lambdaQuery(QcTemplate.class)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(QcTemplate::getTemplateCode, keyword)
                        .or().like(QcTemplate::getTemplateName, keyword))
                .eq(StringUtils.hasText(inspectType), QcTemplate::getInspectType, inspectType)
                .orderByDesc(QcTemplate::getId);
        return PageUtils.of(templateMapper, wrapper, pageNum, pageSize, QcTemplateVO::from);
    }

    @Override
    public QcTemplateVO detail(Long id) {
        QcTemplateVO vo = QcTemplateVO.from(requireTemplate(id));
        vo.setItems(templateItemMapper.selectList(Wrappers.lambdaQuery(QcTemplateItem.class)
                        .eq(QcTemplateItem::getTemplateId, id).orderByAsc(QcTemplateItem::getSeq))
                .stream().map(QcTemplateVO.ItemVO::from).toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(QcTemplateForm form) {
        QcTemplate template = new QcTemplate();
        applyForm(template, form);
        template.setTemplateCode(StringUtils.hasText(form.getTemplateCode())
                ? form.getTemplateCode()
                : "QT" + System.currentTimeMillis());
        template.setStatus(1);
        templateMapper.insert(template);
        insertItems(template.getId(), form);
        return template.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, QcTemplateForm form) {
        QcTemplate template = requireTemplate(id);
        applyForm(template, form);
        if (!template.getTemplateCode().equals(form.getTemplateCode())
                && StringUtils.hasText(form.getTemplateCode())) {
            template.setTemplateCode(form.getTemplateCode());
        }
        templateMapper.updateById(template);
        // 项目行全删全插（模板行数少，代价可接受）
        templateItemMapper.delete(Wrappers.lambdaQuery(QcTemplateItem.class)
                .eq(QcTemplateItem::getTemplateId, id));
        insertItems(id, form);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireTemplate(id);
        templateItemMapper.delete(Wrappers.lambdaQuery(QcTemplateItem.class)
                .eq(QcTemplateItem::getTemplateId, id));
        templateMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        QcTemplate template = requireTemplate(id);
        template.setStatus(status != null && status == 1 ? 1 : 0);
        templateMapper.updateById(template);
    }

    private void applyForm(QcTemplate template, QcTemplateForm form) {
        template.setTemplateName(form.getTemplateName());
        template.setCategoryId(form.getCategoryId());
        template.setInspectType(form.getInspectType());
        template.setRemark(form.getRemark());
    }

    private void insertItems(Long templateId, QcTemplateForm form) {
        int seq = 1;
        for (QcTemplateForm.Item item : form.getItems()) {
            QcTemplateItem row = new QcTemplateItem();
            row.setTemplateId(templateId);
            row.setItemName(item.getItemName());
            row.setStandardValue(item.getStandardValue());
            row.setLowerLimit(item.getLowerLimit());
            row.setUpperLimit(item.getUpperLimit());
            row.setInspectMethod(item.getInspectMethod());
            row.setSampleRate(item.getSampleRate());
            row.setSeq(item.getSeq() != null ? item.getSeq() : seq);
            row.setRemark(item.getRemark());
            templateItemMapper.insert(row);
            seq++;
        }
    }

    private QcTemplate requireTemplate(Long id) {
        QcTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "检验模板不存在: %d".formatted(id));
        }
        return template;
    }
}
