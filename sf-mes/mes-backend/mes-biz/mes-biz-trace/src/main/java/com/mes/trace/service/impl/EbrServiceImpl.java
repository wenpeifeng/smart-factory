package com.mes.trace.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mes.basedata.entity.BaseMaterial;
import com.mes.basedata.mapper.BaseMaterialMapper;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.execution.entity.ExeStationRecord;
import com.mes.execution.mapper.ExeStationRecordMapper;
import com.mes.framework.mybatis.PageUtils;
import com.mes.material.entity.MatPicking;
import com.mes.material.entity.MatPickingItem;
import com.mes.material.mapper.MatPickingItemMapper;
import com.mes.material.mapper.MatPickingMapper;
import com.mes.plan.dto.vo.WorkorderDetailVO;
import com.mes.plan.entity.PlanWorkorder;
import com.mes.plan.mapper.PlanWorkorderMapper;
import com.mes.plan.service.WorkorderService;
import com.mes.quality.entity.QcInspect;
import com.mes.quality.mapper.QcInspectMapper;
import com.mes.trace.dto.vo.TraceEbrVO;
import com.mes.trace.entity.TraceEbr;
import com.mes.trace.mapper.TraceEbrMapper;
import com.mes.trace.service.EbrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 电子批记录服务实现。
 * <p>聚合口径：工单基础 + 工序任务 + 过站明细 + 领料批次 + 关联质检单，
 * 生成后只读（唯一约束 uk_ebr_workorder 保证一单一份）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EbrServiceImpl implements EbrService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final TraceEbrMapper ebrMapper;
    private final PlanWorkorderMapper workorderMapper;
    private final WorkorderService workorderService;
    private final ExeStationRecordMapper stationRecordMapper;
    private final MatPickingMapper pickingMapper;
    private final MatPickingItemMapper pickingItemMapper;
    private final QcInspectMapper inspectMapper;
    private final BaseMaterialMapper materialMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generate(Long workorderId) {
        TraceEbr existed = ebrMapper.selectOne(Wrappers.lambdaQuery(TraceEbr.class)
                .eq(TraceEbr::getWorkorderId, workorderId).last("LIMIT 1"));
        if (existed != null) {
            return existed.getId();
        }
        PlanWorkorder wo = workorderMapper.selectById(workorderId);
        if (wo == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "工单不存在: %d".formatted(workorderId));
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("workorder", wo);
        content.put("steps", workorderService.detail(workorderId).getSteps());
        content.put("stationRecords", stationRecords(workorderId));
        content.put("pickings", pickings(workorderId));
        content.put("inspections", inspectMapper.selectList(Wrappers.lambdaQuery(QcInspect.class)
                .eq(QcInspect::getWorkorderId, workorderId)));

        TraceEbr ebr = new TraceEbr();
        ebr.setEbrNo("EBR" + System.currentTimeMillis());
        ebr.setWorkorderId(wo.getId());
        ebr.setWoNo(wo.getWoNo());
        ebr.setMaterialId(wo.getMaterialId());
        ebr.setBatchNo("WO:" + wo.getWoNo());
        ebr.setContent(toJson(content));
        ebr.setGenerateTime(LocalDateTime.now());
        ebrMapper.insert(ebr);
        log.info("[EBR] 生成电子批记录: wo={} ebr={}", wo.getWoNo(), ebr.getEbrNo());
        return ebr.getId();
    }

    @Override
    public PageResult<TraceEbrVO> page(long pageNum, long pageSize, String keyword) {
        var wrapper = Wrappers.lambdaQuery(TraceEbr.class)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(TraceEbr::getEbrNo, keyword)
                        .or().like(TraceEbr::getWoNo, keyword)
                        .or().like(TraceEbr::getBatchNo, keyword))
                .orderByDesc(TraceEbr::getId);
        PageResult<TraceEbrVO> page = PageUtils.of(ebrMapper, wrapper, pageNum, pageSize, TraceEbrVO::from);
        page.getRecords().forEach(r -> r.setContent(null));
        return page;
    }

    @Override
    public TraceEbrVO detail(Long id) {
        TraceEbr ebr = ebrMapper.selectById(id);
        if (ebr == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "批记录不存在: %d".formatted(id));
        }
        TraceEbrVO vo = TraceEbrVO.from(ebr);
        BaseMaterial mat = materialMapper.selectById(ebr.getMaterialId());
        if (mat != null) {
            vo.setMaterialCode(mat.getCode());
            vo.setMaterialName(mat.getName());
        }
        return vo;
    }

    private List<ExeStationRecord> stationRecords(Long workorderId) {
        return stationRecordMapper.selectList(Wrappers.lambdaQuery(ExeStationRecord.class)
                .eq(ExeStationRecord::getWorkorderId, workorderId)
                .orderByAsc(ExeStationRecord::getId));
    }

    private List<Map<String, Object>> pickings(Long workorderId) {
        List<MatPicking> pickings = pickingMapper.selectList(Wrappers.lambdaQuery(MatPicking.class)
                .eq(MatPicking::getWorkorderId, workorderId));
        return pickings.stream().map(p -> {
            List<MatPickingItem> items = pickingItemMapper.selectList(
                    Wrappers.lambdaQuery(MatPickingItem.class).eq(MatPickingItem::getPickingId, p.getId()));
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("pickNo", p.getPickNo());
            m.put("auditTime", p.getAuditTime());
            m.put("items", items);
            return m;
        }).toList();
    }

    private String toJson(Object content) {
        try {
            return MAPPER.writeValueAsString(content);
        } catch (Exception e) {
            throw new BusinessException("批记录序列化失败: %s", e.getMessage());
        }
    }
}
