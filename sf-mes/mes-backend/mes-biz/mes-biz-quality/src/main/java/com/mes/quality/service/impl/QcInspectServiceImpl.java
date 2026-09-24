package com.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.entity.BaseMaterial;
import com.mes.basedata.mapper.BaseMaterialMapper;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.integration.erp.dto.ErpQualityResultDTO;
import com.mes.integration.service.ErpPushService;
import com.mes.quality.dto.form.QcInspectForm;
import com.mes.quality.dto.form.QcInspectItemFillForm;
import com.mes.quality.dto.vo.QcInspectVO;
import com.mes.quality.entity.QcInspect;
import com.mes.quality.entity.QcInspectItem;
import com.mes.quality.entity.QcTemplate;
import com.mes.quality.entity.QcTemplateItem;
import com.mes.quality.mapper.QcInspectItemMapper;
import com.mes.quality.mapper.QcInspectMapper;
import com.mes.quality.mapper.QcTemplateItemMapper;
import com.mes.quality.mapper.QcTemplateMapper;
import com.mes.quality.service.QcInspectService;
import com.mes.quality.service.QcNoGenerator;
import com.mes.quality.service.impl.QcSpcServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 检验单服务实现。
 * <p>行结论自动判定：实测值可解析为数值时按上下限比较，否则视为目测项
 * （"OK"/"合格" 合格，其余不合格）；无上下限的数值项不做比较记合格。
 * 提交时回传 ERP（失败不阻断提交，推送日志重试）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QcInspectServiceImpl implements QcInspectService {

    private final QcInspectMapper inspectMapper;
    private final QcInspectItemMapper inspectItemMapper;
    private final QcTemplateMapper templateMapper;
    private final QcTemplateItemMapper templateItemMapper;
    private final BaseMaterialMapper materialMapper;
    private final QcNoGenerator noGenerator;
    private final QcSpcServiceImpl spcService;
    private final ErpPushService erpPushService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(QcInspectForm form) {
        QcTemplate template = templateMapper.selectById(form.getTemplateId());
        if (template == null || template.getStatus() == null || template.getStatus() != 1) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "检验模板不存在或未启用: %d".formatted(form.getTemplateId()));
        }
        List<QcTemplateItem> templateItems = templateItemMapper.selectList(
                Wrappers.lambdaQuery(QcTemplateItem.class)
                        .eq(QcTemplateItem::getTemplateId, template.getId())
                        .orderByAsc(QcTemplateItem::getSeq));
        if (templateItems.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "检验模板 %s 无项目行", template.getTemplateCode());
        }
        QcInspect inspect = new QcInspect();
        inspect.setInspectNo(noGenerator.next(QcNoGenerator.PREFIX_INSPECT));
        inspect.setInspectType(form.getInspectType());
        inspect.setSourceType(form.getSourceType());
        inspect.setSourceId(form.getSourceId());
        inspect.setSourceNo(form.getSourceNo());
        inspect.setTemplateId(template.getId());
        inspect.setWorkorderId(form.getWorkorderId());
        inspect.setWoStepId(form.getWoStepId());
        inspect.setMaterialId(form.getMaterialId());
        inspect.setSupplierId(form.getSupplierId());
        inspect.setBatchNo(form.getBatchNo());
        inspect.setSampleQty(form.getSampleQty());
        inspect.setInspectorName(form.getInspectorName());
        inspect.setRemark(form.getRemark());
        inspect.setStatus(0);
        inspectMapper.insert(inspect);
        for (QcTemplateItem ti : templateItems) {
            QcInspectItem item = new QcInspectItem();
            item.setInspectId(inspect.getId());
            item.setItemName(ti.getItemName());
            item.setStandardValue(ti.getStandardValue());
            item.setLowerLimit(ti.getLowerLimit());
            item.setUpperLimit(ti.getUpperLimit());
            item.setInspectMethod(ti.getInspectMethod());
            item.setSampleRate(ti.getSampleRate());
            item.setSeq(ti.getSeq());
            item.setRemark(ti.getRemark());
            inspectItemMapper.insert(item);
        }
        return inspect.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void fillValues(Long inspectId, List<QcInspectItemFillForm> fills) {
        requireDraft(inspectId);
        List<QcInspectItem> items = inspectItemMapper.selectList(
                Wrappers.lambdaQuery(QcInspectItem.class).eq(QcInspectItem::getInspectId, inspectId));
        Map<Long, QcInspectItem> itemMap = items.stream()
                .collect(Collectors.toMap(QcInspectItem::getId, Function.identity()));
        for (QcInspectItemFillForm fill : fills) {
            QcInspectItem item = itemMap.get(fill.getItemId());
            if (item == null) {
                throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "检验项目不属于该检验单: %d".formatted(fill.getItemId()));
            }
            item.setActualValue(fill.getActualValue());
            item.setLineResult(judge(item, fill.getActualValue()));
            inspectItemMapper.updateById(item);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long inspectId) {
        QcInspect inspect = requireDraft(inspectId);
        List<QcInspectItem> items = inspectItemMapper.selectList(
                Wrappers.lambdaQuery(QcInspectItem.class).eq(QcInspectItem::getInspectId, inspectId));
        long unfilled = items.stream().filter(i -> i.getActualValue() == null).count();
        if (unfilled > 0) {
            throw new BusinessException("还有 %d 个检验项目未录入实测值", unfilled);
        }
        long unqualified = items.stream().filter(i -> i.getLineResult() != null && i.getLineResult() == 0).count();
        inspect.setQualifiedQty(inspect.getSampleQty());
        inspect.setUnqualifiedQty(BigDecimal.valueOf(unqualified));
        inspect.setConclusion(unqualified == 0 ? 1 : 0);
        inspect.setInspectDate(LocalDate.now());
        inspect.setStatus(1);
        inspect.setSubmitTime(LocalDateTime.now());
        inspectMapper.updateById(inspect);

        // SPC 采样：命中物料+项目规则的数值型实检值自动落样本并判异
        try {
            spcService.collect(inspect, items);
        } catch (Exception ex) {
            log.warn("[QC] SPC 采样失败（不影响检验提交）: {}", ex.getMessage());
        }

        // 质检结果回传 ERP（幂等+重试由推送日志保证；失败不回滚提交）
        try {
            BaseMaterial mat = materialMapper.selectById(inspect.getMaterialId());
            ErpQualityResultDTO dto = new ErpQualityResultDTO();
            dto.setInspectNo(inspect.getInspectNo());
            dto.setInspectType(inspect.getInspectType());
            dto.setSourceNo(inspect.getSourceNo());
            dto.setMaterialCode(mat != null ? mat.getCode() : String.valueOf(inspect.getMaterialId()));
            dto.setBatchNo(inspect.getBatchNo());
            dto.setSampleQty(inspect.getSampleQty());
            dto.setQualifiedQty(inspect.getQualifiedQty());
            dto.setUnqualifiedQty(inspect.getUnqualifiedQty());
            dto.setConclusion(inspect.getConclusion());
            dto.setInspectorName(inspect.getInspectorName());
            dto.setInspectDate(inspect.getInspectDate());
            erpPushService.pushQualityResult(inspect.getId(), dto);
        } catch (Exception ex) {
            log.warn("[QC] 质检结果回传 ERP 失败（不影响提交）: {}", ex.getMessage());
        }
    }

    @Override
    public PageResult<QcInspectVO> page(long pageNum, long pageSize, String keyword, String inspectType, Integer status) {
        var wrapper = Wrappers.lambdaQuery(QcInspect.class)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(QcInspect::getInspectNo, keyword)
                        .or().like(QcInspect::getSourceNo, keyword))
                .eq(StringUtils.hasText(inspectType), QcInspect::getInspectType, inspectType)
                .eq(status != null, QcInspect::getStatus, status)
                .orderByDesc(QcInspect::getId);
        PageResult<QcInspectVO> page = PageUtils.of(inspectMapper, wrapper, pageNum, pageSize, QcInspectVO::from);
        fillMaterial(page.getRecords());
        return page;
    }

    @Override
    public QcInspectVO detail(Long id) {
        QcInspect inspect = inspectMapper.selectById(id);
        if (inspect == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "检验单不存在: %d".formatted(id));
        }
        QcInspectVO vo = QcInspectVO.from(inspect);
        vo.setItems(inspectItemMapper.selectList(Wrappers.lambdaQuery(QcInspectItem.class)
                        .eq(QcInspectItem::getInspectId, id).orderByAsc(QcInspectItem::getSeq))
                .stream().map(QcInspectVO.ItemVO::from).toList());
        fillMaterial(List.of(vo));
        return vo;
    }

    /** 行结论判定：数值项按上下限；目测项按 OK/合格 关键词 */
    private Integer judge(QcInspectItem item, String actualValue) {
        try {
            BigDecimal value = new BigDecimal(actualValue.trim());
            if (item.getLowerLimit() != null && value.compareTo(item.getLowerLimit()) < 0) {
                return 0;
            }
            if (item.getUpperLimit() != null && value.compareTo(item.getUpperLimit()) > 0) {
                return 0;
            }
            return 1;
        } catch (NumberFormatException e) {
            String normalized = actualValue.trim();
            return "OK".equalsIgnoreCase(normalized) || "合格".equals(normalized) ? 1 : 0;
        }
    }

    private QcInspect requireDraft(Long id) {
        QcInspect inspect = inspectMapper.selectById(id);
        if (inspect == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "检验单不存在: %d".formatted(id));
        }
        if (inspect.getStatus() != null && inspect.getStatus() == 1) {
            throw new BusinessException("检验单已提交，不可再修改");
        }
        return inspect;
    }

    private void fillMaterial(List<QcInspectVO> records) {
        if (records.isEmpty()) {
            return;
        }
        Map<Long, BaseMaterial> mats = materialMapper.selectBatchIds(
                        records.stream().map(QcInspectVO::getMaterialId).toList())
                .stream().collect(Collectors.toMap(BaseMaterial::getId, Function.identity()));
        records.forEach(vo -> {
            BaseMaterial mat = mats.get(vo.getMaterialId());
            if (mat != null) {
                vo.setMaterialCode(mat.getCode());
                vo.setMaterialName(mat.getName());
            }
        });
    }
}
