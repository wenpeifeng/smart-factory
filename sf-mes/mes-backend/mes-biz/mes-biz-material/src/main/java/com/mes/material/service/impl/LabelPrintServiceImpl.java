package com.mes.material.service.impl;

import com.mes.material.dto.form.LabelPrintForm;
import com.mes.material.entity.MatLabelLog;
import com.mes.material.mapper.MatLabelLogMapper;
import com.mes.material.service.LabelPrintService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 标签打印服务实现（打印日志留痕；标签内容由前端模板渲染）。
 */
@Service
@RequiredArgsConstructor
public class LabelPrintServiceImpl implements LabelPrintService {

    private final MatLabelLogMapper labelLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long record(LabelPrintForm form) {
        MatLabelLog log = new MatLabelLog();
        log.setObjType(form.getObjType());
        log.setObjId(form.getObjId());
        log.setObjNo(form.getObjNo());
        log.setTemplate(form.getTemplate());
        log.setCopies(form.getCopies() == null ? 1 : form.getCopies());
        log.setPrinter(form.getPrinter());
        log.setPrintTime(LocalDateTime.now());
        labelLogMapper.insert(log);
        return log.getId();
    }
}
