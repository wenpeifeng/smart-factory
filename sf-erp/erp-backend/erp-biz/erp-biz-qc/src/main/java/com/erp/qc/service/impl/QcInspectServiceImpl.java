package com.erp.qc.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.qc.dto.form.QcInspectForm;
import com.erp.qc.dto.form.QcInspectSubmitForm;
import com.erp.qc.dto.form.QcTemplateForm;
import com.erp.qc.dto.query.QcInspectQuery;
import com.erp.qc.dto.vo.InspectPendingVO;
import com.erp.qc.dto.vo.QcInspectVO;
import com.erp.qc.dto.vo.QcStatisticsVO;
import com.erp.qc.entity.QcInspect;
import com.erp.qc.entity.QcInspectItem;
import com.erp.qc.entity.QcTemplate;
import com.erp.qc.entity.QcTemplateItem;
import com.erp.qc.mapper.CrossModuleMapper;
import com.erp.qc.mapper.QcInspectItemMapper;
import com.erp.qc.mapper.QcInspectMapper;
import com.erp.qc.mapper.QcTemplateItemMapper;
import com.erp.qc.mapper.QcTemplateMapper;
import com.erp.qc.service.QcInspectService;
import com.erp.qc.service.QcNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检验单 Service 实现。
 * <p>
 * 核心约定：
 * <ul>
 *   <li>状态机 0草稿→10已检验 单向，提交用条件 UPDATE 防并发重复提交；</li>
 *   <li>明细行是模板快照：生成时复制模板项目行，模板后续变更不追溯；</li>
 *   <li>模板匹配：检验类型一致 + 物料分类精确优先、通用（分类为空）兜底，仅启用模板；</li>
 *   <li>抽检数量默认 = 来源数量 × 模板最大抽检比例%（向上取整，至少 1）；</li>
 *   <li>口径校验：合格数+不合格数=抽检数；结论=1(合格) ⇔ 不合格数=0。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class QcInspectServiceImpl implements QcInspectService {

    private final QcInspectMapper inspectMapper;
    private final QcInspectItemMapper inspectItemMapper;
    private final QcTemplateMapper templateMapper;
    private final QcTemplateItemMapper templateItemMapper;
    private final CrossModuleMapper crossModuleMapper;
    private final QcNoGenerator noGenerator;

    // ==================== 查询 ====================

    @Override
    public PageResult<QcInspectVO> page(QcInspectQuery query) {
        var page = inspectMapper.selectInspectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public QcInspectVO getDetail(Long id) {
        QcInspect inspect = requireInspect(id);
        QcInspectVO vo = new QcInspectVO();
        vo.setId(inspect.getId());
        vo.setInspectNo(inspect.getInspectNo());
        vo.setInspectType(inspect.getInspectType());
        vo.setSourceType(inspect.getSourceType());
        vo.setSourceId(inspect.getSourceId());
        vo.setSourceNo(inspect.getSourceNo());
        vo.setTemplateId(inspect.getTemplateId());
        if (inspect.getTemplateId() != null) {
            QcTemplate template = templateMapper.selectById(inspect.getTemplateId());
            vo.setTemplateName(template == null ? null : template.getTemplateName());
        }
        vo.setMaterialId(inspect.getMaterialId());
        CrossModuleMapper.MaterialRef material = crossModuleMapper.findMaterial(inspect.getMaterialId());
        if (material != null) {
            vo.setMaterialCode(material.getCode());
            vo.setMaterialName(material.getName());
            vo.setSpec(material.getSpec());
            vo.setUnit(material.getUnit());
        }
        vo.setSupplierId(inspect.getSupplierId());
        vo.setSupplierName(inspect.getSupplierId() == null
                ? null : crossModuleMapper.selectSupplierName(inspect.getSupplierId()));
        vo.setSampleQty(inspect.getSampleQty());
        vo.setQualifiedQty(inspect.getQualifiedQty());
        vo.setUnqualifiedQty(inspect.getUnqualifiedQty());
        vo.setConclusion(inspect.getConclusion());
        vo.setInspectorName(inspect.getInspectorName());
        vo.setInspectDate(inspect.getInspectDate());
        vo.setStatus(inspect.getStatus());
        vo.setSubmitTime(inspect.getSubmitTime());
        vo.setRemark(inspect.getRemark());
        vo.setCreateTime(inspect.getCreateTime());
        vo.setItems(inspectItemMapper.selectByInspectId(id).stream().map(this::toItemVO).toList());
        return vo;
    }

    // ==================== 手工新增/修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(QcInspectForm form) {
        validateForm(form);
        // 明细未传：按 检验类型+物料分类 匹配启用模板快照带出
        List<QcInspectItem> items = form.getItems() == null
                ? snapshotFromTemplate(form.getInspectType(), form.getMaterialId())
                : buildItemsFromForm(form.getItems());
        QcInspect inspect = buildHeader(form);
        QcTemplate matched = matchTemplate(form.getInspectType(), form.getMaterialId());
        inspect.setTemplateId(matched == null ? null : matched.getId());
        inspectMapper.insert(inspect);
        insertItems(inspect.getId(), items);
        return inspect.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, QcInspectForm form) {
        QcInspect inspect = requireInspect(id);
        requireDraft(inspect);
        validateForm(form);
        QcInspect updated = buildHeader(form);
        updated.setId(id);
        if (form.getItems() != null) {
            // 明细整体覆盖
            inspectItemMapper.delete(Wrappers.lambdaQuery(QcInspectItem.class)
                    .eq(QcInspectItem::getInspectId, id));
            insertItems(id, buildItemsFromForm(form.getItems()));
        }
        inspectMapper.updateById(updated);
    }

    /** 表单公共校验：物料存在、来源信息成对出现且来源单存在 */
    private void validateForm(QcInspectForm form) {
        if (crossModuleMapper.findMaterial(form.getMaterialId()) == null) {
            throw BusinessException.ofKey("erp.qc.material-not-exists", form.getMaterialId());
        }
        if (form.getSourceType() != null) {
            if (form.getSourceId() == null || form.getSourceNo() == null || form.getSourceNo().isBlank()) {
                throw BusinessException.ofKey("erp.qc.source-doc-required");
            }
            requireSourceExists(form.getSourceType(), form.getSourceId());
        }
    }

    /** 表单 → 主表实体（不带 id/模板，由调用方补） */
    private QcInspect buildHeader(QcInspectForm form) {
        QcInspect inspect = new QcInspect();
        inspect.setInspectNo(noGenerator.next(QcNoGenerator.PREFIX_INSPECT));
        inspect.setInspectType(form.getInspectType());
        inspect.setSourceType(form.getSourceType());
        inspect.setSourceId(form.getSourceId());
        inspect.setSourceNo(form.getSourceNo());
        inspect.setMaterialId(form.getMaterialId());
        inspect.setSupplierId(form.getSupplierId());
        inspect.setSampleQty(form.getSampleQty());
        inspect.setQualifiedQty(BigDecimal.ZERO);
        inspect.setUnqualifiedQty(BigDecimal.ZERO);
        inspect.setStatus(QcInspect.STATUS_DRAFT);
        inspect.setRemark(form.getRemark());
        return inspect;
    }

    // ==================== 提交检验（状态机） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, QcInspectSubmitForm form) {
        QcInspect inspect = requireInspect(id);
        requireDraft(inspect);

        // 1) 数量口径：合格数+不合格数=抽检数；结论与不合格数一致性
        BigDecimal total = form.getQualifiedQty().add(form.getUnqualifiedQty());
        if (total.compareTo(inspect.getSampleQty()) != 0) {
            throw BusinessException.ofKey("erp.qc.qualified-plus-unqualified", form.getQualifiedQty().toPlainString(), form.getUnqualifiedQty().toPlainString(), inspect.getSampleQty().toPlainString());
        }
        boolean hasBad = form.getUnqualifiedQty().signum() > 0;
        if (form.getConclusion() == QcInspect.CONCLUSION_PASS && hasBad) {
            throw BusinessException.ofKey("erp.qc.qualified-zero-unqualified");
        }
        if (form.getConclusion() != QcInspect.CONCLUSION_PASS && !hasBad) {
            throw BusinessException.ofKey("erp.qc.concession-unqualified-positive");
        }

        // 2) 实检值行归属校验（先于状态推进，非法直接整体拒绝）
        List<QcInspectItem> items = inspectItemMapper.selectByInspectId(id);
        Set<Long> ownedIds = new HashSet<>();
        items.forEach(item -> ownedIds.add(item.getId()));
        for (QcInspectSubmitForm.SubmitItemForm line : form.getItems()) {
            if (!ownedIds.contains(line.getId())) {
                throw BusinessException.ofKey("erp.qc.result-line-not-in-inspection", line.getId(), inspect.getInspectNo());
            }
        }

        // 3) 状态推进：条件 UPDATE 防并发重复提交（0 行=非草稿）
        int rows = inspectMapper.submit(id, form.getQualifiedQty(), form.getUnqualifiedQty(),
                form.getConclusion(), form.getInspectorName(), form.getInspectDate());
        if (rows == 0) {
            throw BusinessException.ofKey("erp.qc.only-draft-can-submit", inspect.getInspectNo());
        }

        // 4) 回填明细实检值/单项判定 + 表头备注
        for (QcInspectSubmitForm.SubmitItemForm line : form.getItems()) {
            QcInspectItem patch = new QcInspectItem();
            patch.setId(line.getId());
            patch.setActualValue(line.getActualValue());
            patch.setLineResult(line.getLineResult());
            inspectItemMapper.updateById(patch);
        }
        if (form.getRemark() != null) {
            QcInspect header = new QcInspect();
            header.setId(id);
            header.setRemark(form.getRemark());
            inspectMapper.updateById(header);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        QcInspect inspect = requireInspect(id);
        requireDraft(inspect);
        inspectMapper.deleteById(id);
        inspectItemMapper.delete(Wrappers.lambdaQuery(QcInspectItem.class)
                .eq(QcInspectItem::getInspectId, id));
    }

    // ==================== 挂点：按来源单生成 / 卡点查询 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> generateFromBiz(String bizType, Long bizId) {
        List<Long> created = new ArrayList<>();
        switch (bizType == null ? "" : bizType) {
            case QcInspect.SOURCE_PUR_INSTOCK -> {
                CrossModuleMapper.InstockRef ref = requireInstock(bizId);
                for (CrossModuleMapper.SourceLine line : crossModuleMapper.findInstockLines(bizId)) {
                    created.add(createFromSource(bizType, bizId, ref.getInstockNo(),
                            line.getMaterialId(), ref.getSupplierId(), line.getQty(),
                            QcTemplate.TYPE_IQC));
                }
            }
            case QcInspect.SOURCE_MRP_WORK_ORDER -> {
                CrossModuleMapper.WorkOrderRef ref = requireWorkOrder(bizId);
                created.add(createFromSource(bizType, bizId, ref.getOrderNo(),
                        ref.getMaterialId(), null, ref.getQty(), QcTemplate.TYPE_FQC));
            }
            case QcInspect.SOURCE_SAL_RETURN -> {
                CrossModuleMapper.ReturnRef ref = requireSalReturn(bizId);
                for (CrossModuleMapper.SourceLine line : crossModuleMapper.findSalReturnLines(bizId)) {
                    // 销售退货复检按 IQC 口径（系统检验类型仅 IQC/IPQC/FQC/OQC）
                    created.add(createFromSource(bizType, bizId, ref.getReturnNo(),
                            line.getMaterialId(), null, line.getQty(), QcTemplate.TYPE_IQC));
                }
            }
            default -> throw BusinessException.ofKey("erp.qc.source-type-invalid", bizType);
        }
        return created;
    }

    @Override
    public InspectPendingVO pendingOfSource(String bizType, Long bizId) {
        validateBizType(bizType);
        requireSourceExists(bizType, bizId);
        InspectPendingVO vo = new InspectPendingVO();
        vo.setBizType(bizType);
        vo.setBizId(bizId);
        vo.setPendingCount(inspectMapper.countPendingBySource(bizType, bizId));
        vo.setPending(vo.getPendingCount() > 0);
        vo.setPendingList(inspectMapper.selectPendingBySource(bizType, bizId));
        return vo;
    }

    /** 按来源单物料行生成一张草稿检验单；该来源单+物料已有未删除检验单则跳过（幂等） */
    private Long createFromSource(String bizType, Long bizId, String sourceNo,
                                  Long materialId, Long supplierId, BigDecimal baseQty,
                                  String inspectType) {
        Long exists = inspectMapper.selectCount(Wrappers.lambdaQuery(QcInspect.class)
                .eq(QcInspect::getSourceType, bizType)
                .eq(QcInspect::getSourceId, bizId)
                .eq(QcInspect::getMaterialId, materialId));
        if (exists != null && exists > 0) {
            return null;
        }
        QcTemplate template = matchTemplate(inspectType, materialId);
        List<QcInspectItem> items = snapshotFromTemplate(inspectType, materialId);
        QcInspect inspect = new QcInspect();
        inspect.setInspectNo(noGenerator.next(QcNoGenerator.PREFIX_INSPECT));
        inspect.setInspectType(inspectType);
        inspect.setSourceType(bizType);
        inspect.setSourceId(bizId);
        inspect.setSourceNo(sourceNo);
        inspect.setTemplateId(template == null ? null : template.getId());
        inspect.setMaterialId(materialId);
        inspect.setSupplierId(supplierId);
        inspect.setSampleQty(defaultSampleQty(baseQty, items));
        inspect.setQualifiedQty(BigDecimal.ZERO);
        inspect.setUnqualifiedQty(BigDecimal.ZERO);
        inspect.setStatus(QcInspect.STATUS_DRAFT);
        inspectMapper.insert(inspect);
        insertItems(inspect.getId(), items);
        return inspect.getId();
    }

    /** 默认抽检数 = 来源数量 × 模板最大抽检比例%（向上取整，至少 1） */
    private BigDecimal defaultSampleQty(BigDecimal baseQty, List<QcInspectItem> items) {
        BigDecimal rate = BigDecimal.valueOf(100);
        for (QcInspectItem item : items) {
            if (item.getSampleRate() != null && item.getSampleRate().compareTo(rate) > 0) {
                rate = item.getSampleRate();
            }
        }
        BigDecimal base = baseQty == null || baseQty.signum() <= 0
                ? BigDecimal.ONE : baseQty;
        BigDecimal sample = base.multiply(rate).divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING);
        return sample.max(BigDecimal.ONE);
    }

    // ==================== 统计 ====================

    @Override
    public QcStatisticsVO statistics(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate begin = from == null ? end.minusDays(29) : from;
        if (begin.isAfter(end)) {
            throw BusinessException.ofKey("erp.qc.stat-range-invalid", begin, end);
        }
        QcStatisticsVO vo = new QcStatisticsVO();
        vo.setFromDate(begin);
        vo.setToDate(end);

        List<QcStatisticsVO.MaterialStatVO> materialStats = inspectMapper.selectMaterialStats(begin, end);
        materialStats.forEach(QcStatisticsVO.MaterialStatVO::fillPassRate);
        vo.setMaterialStats(materialStats);

        List<QcStatisticsVO.SupplierStatVO> supplierStats = inspectMapper.selectSupplierStats(begin, end);
        supplierStats.forEach(QcStatisticsVO.SupplierStatVO::fillPassRate);
        vo.setSupplierStats(supplierStats);

        // 趋势补零：SQL 只返回有数据的日期，图表需要连续区间
        Map<LocalDate, QcStatisticsVO.TrendPointVO> byDate = new HashMap<>();
        for (QcStatisticsVO.TrendPointVO point : inspectMapper.selectTrend(begin, end)) {
            byDate.put(point.getDate(), point);
        }
        List<QcStatisticsVO.TrendPointVO> trend = new ArrayList<>();
        for (LocalDate day = begin; !day.isAfter(end); day = day.plusDays(1)) {
            QcStatisticsVO.TrendPointVO point = byDate.get(day);
            if (point == null) {
                point = new QcStatisticsVO.TrendPointVO();
                point.setDate(day);
                point.setCount(0L);
                point.setSampleQty(BigDecimal.ZERO);
            }
            trend.add(point);
        }
        vo.setTrend(trend);
        return vo;
    }

    // ==================== 私有工具 ====================

    /** 加载检验单，不存在抛业务异常 */
    private QcInspect requireInspect(Long id) {
        QcInspect inspect = inspectMapper.selectById(id);
        if (inspect == null) {
            throw BusinessException.ofKey("erp.qc.inspection-not-exists", id);
        }
        return inspect;
    }

    /** 校验为草稿状态 */
    private void requireDraft(QcInspect inspect) {
        if (inspect.getStatus() != QcInspect.STATUS_DRAFT) {
            throw BusinessException.ofKey("erp.qc.only-draft-allowed", inspect.getInspectNo());
        }
    }

    /** 校验来源单据类型合法 */
    private void validateBizType(String bizType) {
        if (!QcInspect.SOURCE_PUR_INSTOCK.equals(bizType)
                && !QcInspect.SOURCE_MRP_WORK_ORDER.equals(bizType)
                && !QcInspect.SOURCE_SAL_RETURN.equals(bizType)) {
            throw BusinessException.ofKey("erp.qc.source-type-invalid", bizType);
        }
    }

    /** 校验来源单存在（按约定表只读查询），不存在抛业务异常 */
    private void requireSourceExists(String bizType, Long bizId) {
        switch (bizType) {
            case QcInspect.SOURCE_PUR_INSTOCK -> {
                if (crossModuleMapper.findInstock(bizId) == null) {
                    throw BusinessException.ofKey("erp.qc.pur-instock-not-exists", bizId);
                }
            }
            case QcInspect.SOURCE_MRP_WORK_ORDER -> {
                if (crossModuleMapper.findWorkOrder(bizId) == null) {
                    throw BusinessException.ofKey("erp.qc.work-order-not-exists", bizId);
                }
            }
            case QcInspect.SOURCE_SAL_RETURN -> {
                if (crossModuleMapper.findSalReturn(bizId) == null) {
                    throw BusinessException.ofKey("erp.qc.sal-return-not-exists", bizId);
                }
            }
            default -> validateBizType(bizType);
        }
    }

    private CrossModuleMapper.InstockRef requireInstock(Long bizId) {
        CrossModuleMapper.InstockRef ref = crossModuleMapper.findInstock(bizId);
        if (ref == null) {
            throw BusinessException.ofKey("erp.qc.pur-instock-not-exists", bizId);
        }
        return ref;
    }

    private CrossModuleMapper.WorkOrderRef requireWorkOrder(Long bizId) {
        CrossModuleMapper.WorkOrderRef ref = crossModuleMapper.findWorkOrder(bizId);
        if (ref == null) {
            throw BusinessException.ofKey("erp.qc.work-order-not-exists", bizId);
        }
        return ref;
    }

    private CrossModuleMapper.ReturnRef requireSalReturn(Long bizId) {
        CrossModuleMapper.ReturnRef ref = crossModuleMapper.findSalReturn(bizId);
        if (ref == null) {
            throw BusinessException.ofKey("erp.qc.sal-return-not-exists", bizId);
        }
        return ref;
    }

    /**
     * 模板匹配：检验类型一致 + 启用，物料分类精确匹配优先，通用模板（分类为空）兜底。
     *
     * @return 匹配不到返回 null（生成空明细检验单，允许手工补项目）
     */
    private QcTemplate matchTemplate(String inspectType, Long materialId) {
        Long categoryId = null;
        CrossModuleMapper.MaterialRef material = crossModuleMapper.findMaterial(materialId);
        if (material != null) {
            categoryId = material.getCategoryId();
        }
        QcTemplate template = null;
        if (categoryId != null) {
            template = templateMapper.selectOne(Wrappers.lambdaQuery(QcTemplate.class)
                    .eq(QcTemplate::getInspectType, inspectType)
                    .eq(QcTemplate::getCategoryId, categoryId)
                    .eq(QcTemplate::getStatus, QcTemplate.STATUS_ENABLED)
                    .orderByAsc(QcTemplate::getId)
                    .last("LIMIT 1"));
        }
        if (template == null) {
            template = templateMapper.selectOne(Wrappers.lambdaQuery(QcTemplate.class)
                    .eq(QcTemplate::getInspectType, inspectType)
                    .isNull(QcTemplate::getCategoryId)
                    .eq(QcTemplate::getStatus, QcTemplate.STATUS_ENABLED)
                    .orderByAsc(QcTemplate::getId)
                    .last("LIMIT 1"));
        }
        return template;
    }

    /** 匹配模板并快照其项目行为检验单明细（无匹配模板返回空列表） */
    private List<QcInspectItem> snapshotFromTemplate(String inspectType, Long materialId) {
        QcTemplate template = matchTemplate(inspectType, materialId);
        if (template == null) {
            return new ArrayList<>();
        }
        List<QcInspectItem> items = new ArrayList<>();
        for (QcTemplateItem src : templateItemMapper.selectByTemplateId(template.getId())) {
            QcInspectItem item = new QcInspectItem();
            item.setItemName(src.getItemName());
            item.setStandardValue(src.getStandardValue());
            item.setLowerLimit(src.getLowerLimit());
            item.setUpperLimit(src.getUpperLimit());
            item.setInspectMethod(src.getInspectMethod());
            item.setSampleRate(src.getSampleRate());
            item.setSeq(src.getSeq());
            item.setRemark(src.getRemark());
            items.add(item);
        }
        return items;
    }

    /** 表单项目行 → 检验单明细实体（手工指定明细，覆盖模板） */
    private List<QcInspectItem> buildItemsFromForm(List<QcTemplateForm.TemplateItemForm> lines) {
        List<QcInspectItem> items = new ArrayList<>();
        for (QcTemplateForm.TemplateItemForm line : lines) {
            if (line.getLowerLimit() != null && line.getUpperLimit() != null
                    && line.getLowerLimit().compareTo(line.getUpperLimit()) > 0) {
                throw BusinessException.ofKey("erp.qc.item-lower-over-upper", line.getItemName());
            }
            QcInspectItem item = new QcInspectItem();
            item.setItemName(line.getItemName());
            item.setStandardValue(line.getStandardValue());
            item.setLowerLimit(line.getLowerLimit());
            item.setUpperLimit(line.getUpperLimit());
            item.setInspectMethod(line.getInspectMethod());
            item.setSampleRate(line.getSampleRate());
            item.setSeq(line.getSeq() == null ? 0 : line.getSeq());
            item.setRemark(line.getRemark());
            items.add(item);
        }
        return items;
    }

    /** 插入明细行（回填 inspectId） */
    private void insertItems(Long inspectId, List<QcInspectItem> items) {
        items.forEach(item -> {
            item.setInspectId(inspectId);
            inspectItemMapper.insert(item);
        });
    }

    /** 明细 Entity → VO */
    private QcInspectVO.QcInspectItemVO toItemVO(QcInspectItem item) {
        QcInspectVO.QcInspectItemVO vo = new QcInspectVO.QcInspectItemVO();
        vo.setId(item.getId());
        vo.setItemName(item.getItemName());
        vo.setStandardValue(item.getStandardValue());
        vo.setLowerLimit(item.getLowerLimit());
        vo.setUpperLimit(item.getUpperLimit());
        vo.setInspectMethod(item.getInspectMethod());
        vo.setSampleRate(item.getSampleRate());
        vo.setActualValue(item.getActualValue());
        vo.setLineResult(item.getLineResult());
        vo.setSeq(item.getSeq());
        vo.setRemark(item.getRemark());
        return vo;
    }
}
