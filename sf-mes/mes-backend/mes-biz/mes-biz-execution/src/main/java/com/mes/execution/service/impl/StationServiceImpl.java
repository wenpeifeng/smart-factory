package com.mes.execution.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.framework.security.SecurityUtils;
import com.mes.integration.erp.dto.ErpWorkReportDTO;
import com.mes.integration.service.ErpPushService;
import com.mes.execution.dto.form.StationReportForm;
import com.mes.execution.dto.query.StationRecordQuery;
import com.mes.execution.dto.vo.StationRecordVO;
import com.mes.execution.entity.ExeStationRecord;
import com.mes.execution.entity.ExeWip;
import com.mes.execution.mapper.ExeStationRecordMapper;
import com.mes.execution.mapper.ExeWipMapper;
import com.mes.execution.service.StationService;
import com.mes.plan.dto.vo.WoStepVO;
import com.mes.plan.entity.PlanWorkorder;
import com.mes.plan.mapper.PlanWorkorderMapper;
import com.mes.plan.service.MesNoGenerator;
import com.mes.plan.service.WorkorderService;
import com.mes.plan.service.WoStepService;
import com.mes.framework.event.TraceEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 过站扫码报工服务实现。
 * <p>ERP 回传失败不阻断报工主流程（推送日志置 FAILED 由定时任务重试），
 * 故 push 放在事务末尾且捕获异常仅记日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StationServiceImpl implements StationService {

    private final ExeStationRecordMapper stationRecordMapper;
    private final ExeWipMapper wipMapper;
    private final PlanWorkorderMapper workorderMapper;
    private final WoStepService woStepService;
    private final WorkorderService workorderService;
    private final MesNoGenerator noGenerator;
    private final ErpPushService erpPushService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long report(StationReportForm form) {
        WoStepVO step = woStepService.getById(form.getWoStepId());
        PlanWorkorder wo = workorderMapper.selectById(step.getWorkorderId());
        if (wo == null) {
            throw new BusinessException("工单不存在: %d".formatted(step.getWorkorderId()));
        }
        BigDecimal good = nvl(form.getGoodQty());
        BigDecimal scrap = nvl(form.getScrapQty());
        BigDecimal rework = nvl(form.getReworkQty());
        if (good.add(scrap).add(rework).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("合格/报废/返修数量不能同时为 0");
        }

        ExeStationRecord record = new ExeStationRecord();
        record.setRecordNo(noGenerator.next(MesNoGenerator.PREFIX_STATION));
        record.setWorkorderId(wo.getId());
        record.setWoNo(wo.getWoNo());
        record.setWoStepId(step.getId());
        record.setSeqNo(step.getSeqNo());
        record.setOperationId(step.getOperationId());
        record.setOperationName(step.getOperationName());
        record.setWorkcenterId(step.getWorkcenterId());
        record.setDeviceId(form.getDeviceId());
        record.setSn(form.getSn());
        record.setKeyComponentSn(form.getKeyComponentSn());
        record.setGoodQty(good);
        record.setScrapQty(scrap);
        record.setReworkQty(rework);
        record.setWorkMinutes(form.getWorkMinutes());
        Long workerId = null;
        try {
            workerId = SecurityUtils.getCurrentUserId();
        } catch (Exception ignore) {
            // 系统间调用（无登录态）时允许无报工人 id
        }
        record.setWorkerId(workerId);
        record.setWorkerName(StringUtils.hasText(form.getWorkerName()) ? form.getWorkerName() : "系统");
        record.setTeamId(form.getTeamId());
        record.setStationTime(LocalDateTime.now());
        record.setResult("FAIL".equalsIgnoreCase(form.getResult()) ? "FAIL" : "PASS");
        stationRecordMapper.insert(record);

        // WIP 汇总：投入=本笔全部过站量，产出=合格，报废/返修单列；wip=投入-产出-报废
        upsertWip(record, good, scrap, rework);
        // 关键件 SN 绑定追溯事件（trace 模块 AFTER_COMMIT 落 trace_link）
        if (StringUtils.hasText(record.getSn()) && StringUtils.hasText(form.getKeyComponentSn())) {
            eventPublisher.publishEvent(new TraceEvents.StationReported(
                    record.getId(), wo.getId(), step.getId(), record.getSn(),
                    java.util.Arrays.stream(form.getKeyComponentSn().split("[,，]"))
                            .map(String::trim).filter(StringUtils::hasText).toList(),
                    record.getStationTime()));
        }
        // 工序与工单累计（工单首笔报工 DRAFT->RELEASED 后自动转 PRODUCING）
        woStepService.accumulateStation(step.getId(), good, scrap, rework, form.getWorkMinutes());
        workorderService.accumulateReport(wo.getId(), good, scrap);

        // PASS 结果回传 ERP（失败仅记日志，由推送重试任务补偿）
        if ("PASS".equals(record.getResult())) {
            try {
                ErpWorkReportDTO dto = new ErpWorkReportDTO();
                dto.setWoNo(wo.getWoNo());
                dto.setErpOrderNo(wo.getSourceNo());
                dto.setOperationName(step.getOperationName());
                dto.setGoodQty(good);
                dto.setScrapQty(scrap);
                dto.setReworkQty(rework);
                dto.setReportTime(record.getStationTime());
                dto.setWorkerName(record.getWorkerName());
                erpPushService.pushWorkReport(record.getId(), dto);
            } catch (Exception ex) {
                log.warn("[STATION] 完工回传 ERP 失败（不影响报工）: {}", ex.getMessage());
            }
        }
        return record.getId();
    }

    @Override
    public PageResult<StationRecordVO> page(StationRecordQuery query) {
        var wrapper = Wrappers.lambdaQuery(ExeStationRecord.class)
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(ExeStationRecord::getRecordNo, query.getKeyword())
                        .or().like(ExeStationRecord::getWoNo, query.getKeyword())
                        .or().like(ExeStationRecord::getSn, query.getKeyword()))
                .eq(query.getWorkorderId() != null, ExeStationRecord::getWorkorderId, query.getWorkorderId())
                .eq(query.getWoStepId() != null, ExeStationRecord::getWoStepId, query.getWoStepId())
                .eq(StringUtils.hasText(query.getResult()), ExeStationRecord::getResult, query.getResult())
                .orderByDesc(ExeStationRecord::getId);
        return PageUtils.of(stationRecordMapper, wrapper,
                query.getPageNum(), query.getPageSize(), StationRecordVO::from);
    }

    /** 工单 x 工序维度 WIP 汇总 upsert */
    private void upsertWip(ExeStationRecord record, BigDecimal good, BigDecimal scrap, BigDecimal rework) {
        ExeWip wip = wipMapper.selectOne(Wrappers.lambdaQuery(ExeWip.class)
                .eq(ExeWip::getWoStepId, record.getWoStepId())
                .last("LIMIT 1"));
        if (wip == null) {
            wip = new ExeWip();
            wip.setWorkorderId(record.getWorkorderId());
            wip.setWoNo(record.getWoNo());
            wip.setWoStepId(record.getWoStepId());
            wip.setSeqNo(record.getSeqNo());
            wip.setOperationName(record.getOperationName());
            wip.setWorkcenterId(record.getWorkcenterId());
            wip.setInputQty(BigDecimal.ZERO);
            wip.setOutputQty(BigDecimal.ZERO);
            wip.setWipQty(BigDecimal.ZERO);
            wip.setScrapQty(BigDecimal.ZERO);
            wip.setReworkQty(BigDecimal.ZERO);
        }
        wip.setInputQty(nvl(wip.getInputQty()).add(good).add(scrap).add(rework));
        wip.setOutputQty(nvl(wip.getOutputQty()).add(good));
        wip.setScrapQty(nvl(wip.getScrapQty()).add(scrap));
        wip.setReworkQty(nvl(wip.getReworkQty()).add(rework));
        wip.setWipQty(nvl(wip.getInputQty()).subtract(nvl(wip.getOutputQty())).subtract(nvl(wip.getScrapQty())));
        wip.setLastStationTime(record.getStationTime());
        if (wip.getId() == null) {
            wipMapper.insert(wip);
        } else {
            wipMapper.updateById(wip);
        }
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
