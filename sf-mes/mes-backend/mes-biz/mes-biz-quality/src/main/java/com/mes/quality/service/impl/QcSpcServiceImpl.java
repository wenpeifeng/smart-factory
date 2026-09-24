package com.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.entity.BaseMaterial;
import com.mes.basedata.mapper.BaseMaterialMapper;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.quality.dto.form.QcSpcRuleForm;
import com.mes.quality.dto.vo.QcSpcChartVO;
import com.mes.quality.dto.vo.QcSpcRuleVO;
import com.mes.quality.entity.QcInspect;
import com.mes.quality.entity.QcInspectItem;
import com.mes.quality.entity.QcSpcRule;
import com.mes.quality.entity.QcSpcSample;
import com.mes.quality.mapper.QcInspectItemMapper;
import com.mes.quality.mapper.QcSpcRuleMapper;
import com.mes.quality.mapper.QcSpcSampleMapper;
import com.mes.quality.service.QcSpcService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SPC 服务实现。
 * <p>采样入口：检验单提交后由 {@code QcSpcCollector} 调用 {@link #collect(QcInspect, List)}；
 * 判异在"最近样本序列"上按启用准则判定，violation 写回本样本行。
 */
@Service
@RequiredArgsConstructor
public class QcSpcServiceImpl implements QcSpcService {

    private final QcSpcRuleMapper ruleMapper;
    private final QcSpcSampleMapper sampleMapper;
    private final QcInspectItemMapper inspectItemMapper;
    private final BaseMaterialMapper materialMapper;

    @Override
    public PageResult<QcSpcRuleVO> page(long pageNum, long pageSize, String keyword, Long materialId) {
        var wrapper = Wrappers.lambdaQuery(QcSpcRule.class)
                .eq(materialId != null, QcSpcRule::getMaterialId, materialId)
                .like(StringUtils.hasText(keyword), QcSpcRule::getItemName, keyword)
                .orderByDesc(QcSpcRule::getId);
        // 物料编码过滤：先解析物料
        if (StringUtils.hasText(keyword)) {
            List<Long> ids = materialMapper.selectList(Wrappers.lambdaQuery(BaseMaterial.class)
                            .like(BaseMaterial::getCode, keyword))
                    .stream().map(BaseMaterial::getId).toList();
            wrapper = Wrappers.lambdaQuery(QcSpcRule.class)
                    .eq(materialId != null, QcSpcRule::getMaterialId, materialId)
                    .like(QcSpcRule::getItemName, keyword)
                    .or().in(!ids.isEmpty(), QcSpcRule::getMaterialId, ids)
                    .orderByDesc(QcSpcRule::getId);
        }
        PageResult<QcSpcRuleVO> page = PageUtils.of(ruleMapper, wrapper, pageNum, pageSize, QcSpcRuleVO::from);
        fillMaterial(page.getRecords());
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(QcSpcRuleForm form) {
        if (form.getUcl().compareTo(form.getLcl()) <= 0) {
            throw new BusinessException("UCL 必须大于 LCL");
        }
        QcSpcRule rule = ruleMapper.selectOne(Wrappers.lambdaQuery(QcSpcRule.class)
                .eq(QcSpcRule::getMaterialId, form.getMaterialId())
                .eq(QcSpcRule::getItemName, form.getItemName())
                .last("LIMIT 1"));
        if (rule == null) {
            rule = new QcSpcRule();
            rule.setMaterialId(form.getMaterialId());
            rule.setItemName(form.getItemName());
        }
        rule.setUcl(form.getUcl());
        rule.setLcl(form.getLcl());
        rule.setTarget(form.getTarget());
        rule.setRuleR1(form.getRuleR1() != null ? form.getRuleR1() : 1);
        rule.setRuleR2(form.getRuleR2() != null ? form.getRuleR2() : 1);
        rule.setRuleR3(form.getRuleR3() != null ? form.getRuleR3() : 1);
        rule.setRuleR4(form.getRuleR4() != null ? form.getRuleR4() : 0);
        rule.setRuleR5(form.getRuleR5() != null ? form.getRuleR5() : 1);
        rule.setRemark(form.getRemark());
        if (rule.getStatus() == null) {
            rule.setStatus(1);
        }
        if (rule.getId() == null) {
            ruleMapper.insert(rule);
        } else {
            ruleMapper.updateById(rule);
        }
        return rule.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ruleMapper.deleteById(requireRule(id).getId());
        sampleMapper.delete(Wrappers.lambdaQuery(QcSpcSample.class).eq(QcSpcSample::getRuleId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        QcSpcRule rule = requireRule(id);
        rule.setStatus(status != null && status == 1 ? 1 : 0);
        ruleMapper.updateById(rule);
    }

    @Override
    public QcSpcChartVO chart(Long ruleId, int limit) {
        QcSpcRule rule = requireRule(ruleId);
        QcSpcChartVO vo = new QcSpcChartVO();
        QcSpcRuleVO ruleVo = QcSpcRuleVO.from(rule);
        fillMaterial(List.of(ruleVo));
        vo.setRule(ruleVo);
        vo.setCenter(centerOf(rule));
        List<QcSpcChartVO.SampleVO> samples = sampleMapper.selectList(
                        Wrappers.lambdaQuery(QcSpcSample.class)
                                .eq(QcSpcSample::getRuleId, ruleId)
                                .orderByDesc(QcSpcSample::getSampleTime)
                                .last("LIMIT " + Math.max(1, Math.min(limit, 200))))
                .stream().map(s -> {
                    QcSpcChartVO.SampleVO sv = new QcSpcChartVO.SampleVO();
                    sv.setId(s.getId());
                    sv.setValue(s.getValue());
                    sv.setSampleTime(s.getSampleTime());
                    sv.setInspectId(s.getInspectId());
                    sv.setViolations(s.getViolations());
                    return sv;
                }).toList();
        java.util.Collections.reverse(samples);
        vo.setSamples(samples);
        return vo;
    }

    /**
     * 检验单提交后采样：对启用规则的物料，把数值型实检值落样本并判异。
     *
     * @param inspect 检验单
     * @param items   检验项目行
     */
    @Transactional(rollbackFor = Exception.class)
    public void collect(QcInspect inspect, List<QcInspectItem> items) {
        List<QcSpcRule> rules = ruleMapper.selectList(Wrappers.lambdaQuery(QcSpcRule.class)
                .eq(QcSpcRule::getMaterialId, inspect.getMaterialId())
                .eq(QcSpcRule::getStatus, 1));
        if (rules.isEmpty()) {
            return;
        }
        Map<String, QcSpcRule> byItem = rules.stream()
                .collect(Collectors.toMap(QcSpcRule::getItemName, Function.identity()));
        for (QcInspectItem item : items) {
            QcSpcRule rule = byItem.get(item.getItemName());
            if (rule == null) {
                continue;
            }
            BigDecimal value = parseNumeric(item.getActualValue());
            if (value == null) {
                continue;
            }
            QcSpcSample sample = new QcSpcSample();
            sample.setRuleId(rule.getId());
            sample.setInspectId(inspect.getId());
            sample.setValue(value);
            sample.setSampleTime(inspect.getSubmitTime());
            sample.setViolations(judge(rule, value));
            sampleMapper.insert(sample);
        }
    }

    /**
     * 判异：取"历史最近样本 + 当前值"序列按启用准则判定（当前点视角）。
     */
    private String judge(QcSpcRule rule, BigDecimal value) {
        List<QcSpcSample> history = sampleMapper.selectList(Wrappers.lambdaQuery(QcSpcSample.class)
                .eq(QcSpcSample::getRuleId, rule.getId())
                .orderByDesc(QcSpcSample::getSampleTime)
                .last("LIMIT 20"));
        java.util.Collections.reverse(history);
        List<BigDecimal> series = new ArrayList<>();
        history.forEach(s -> series.add(s.getValue()));
        series.add(value);

        BigDecimal center = centerOf(rule);
        BigDecimal zoneB = rule.getUcl().subtract(center).abs()
                .multiply(BigDecimal.valueOf(2)).divide(BigDecimal.valueOf(3), 4, java.math.RoundingMode.HALF_UP);
        List<String> hits = new ArrayList<>();
        int n = series.size();
        // R1 超限
        if (enabled(rule.getRuleR1())
                && (value.compareTo(rule.getUcl()) > 0 || value.compareTo(rule.getLcl()) < 0)) {
            hits.add("R1");
        }
        // R2 连续9点同侧
        if (enabled(rule.getRuleR2()) && n >= 9) {
            boolean same = true;
            boolean above = series.get(n - 1).compareTo(center) > 0;
            for (int i = n - 2; i >= n - 9; i--) {
                if ((series.get(i).compareTo(center) > 0) != above || series.get(i).compareTo(center) == 0) {
                    same = false;
                    break;
                }
            }
            if (same) {
                hits.add("R2");
            }
        }
        // R3 连续6点单调
        if (enabled(rule.getRuleR3()) && n >= 6) {
            boolean up = true;
            boolean down = true;
            for (int i = n - 1; i > n - 6; i--) {
                int cmp = series.get(i).compareTo(series.get(i - 1));
                if (cmp >= 0) {
                    down = false;
                }
                if (cmp <= 0) {
                    up = false;
                }
            }
            if (up || down) {
                hits.add("R3");
            }
        }
        // R5 最近3点中2点落在同侧 B 区外（>中心±2/3 控制限半宽）
        if (enabled(rule.getRuleR5()) && n >= 3) {
            int upperB = 0;
            int lowerB = 0;
            for (int i = n - 3; i < n; i++) {
                BigDecimal dev = series.get(i).subtract(center).abs();
                if (dev.compareTo(zoneB) > 0) {
                    if (series.get(i).compareTo(center) > 0) {
                        upperB++;
                    } else {
                        lowerB++;
                    }
                }
            }
            if (upperB >= 2 || lowerB >= 2) {
                hits.add("R5");
            }
        }
        // R4 连续14点交替（默认停用）
        if (enabled(rule.getRuleR4()) && n >= 14) {
            boolean alt = true;
            for (int i = n - 1; i > n - 13; i--) {
                boolean d1 = series.get(i).compareTo(series.get(i - 1)) > 0;
                boolean d2 = series.get(i - 1).compareTo(series.get(i - 2)) > 0;
                if (d1 == d2) {
                    alt = false;
                    break;
                }
            }
            if (alt) {
                hits.add("R4");
            }
        }
        return hits.isEmpty() ? null : String.join(",", hits);
    }

    private BigDecimal parseNumeric(String actualValue) {
        if (!StringUtils.hasText(actualValue)) {
            return null;
        }
        try {
            return new BigDecimal(actualValue.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 中心线：target 或 (UCL+LCL)/2（指定 scale 防止非整除异常） */
    private BigDecimal centerOf(QcSpcRule rule) {
        return rule.getTarget() != null ? rule.getTarget()
                : rule.getUcl().add(rule.getLcl()).divide(BigDecimal.valueOf(2), 4, java.math.RoundingMode.HALF_UP);
    }

    private boolean enabled(Integer flag) {
        return flag != null && flag == 1;
    }

    private void fillMaterial(List<QcSpcRuleVO> records) {
        if (records.isEmpty()) {
            return;
        }
        Map<Long, BaseMaterial> mats = materialMapper.selectBatchIds(
                        records.stream().map(QcSpcRuleVO::getMaterialId).toList())
                .stream().collect(Collectors.toMap(BaseMaterial::getId, Function.identity()));
        records.forEach(vo -> {
            BaseMaterial mat = mats.get(vo.getMaterialId());
            if (mat != null) {
                vo.setMaterialCode(mat.getCode());
                vo.setMaterialName(mat.getName());
            }
        });
    }

    private QcSpcRule requireRule(Long id) {
        QcSpcRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "SPC规则不存在: %d".formatted(id));
        }
        return rule;
    }
}
