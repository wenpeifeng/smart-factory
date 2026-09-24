package com.erp.mrp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.mrp.constant.MrpShopConstants;
import com.erp.mrp.dto.form.WorkReportForm;
import com.erp.mrp.dto.vo.WorkReportVO;
import com.erp.mrp.entity.MrpOperation;
import com.erp.mrp.entity.MrpWorkOrder;
import com.erp.mrp.entity.MrpWorkReport;
import com.erp.mrp.entity.MrpWoStep;
import com.erp.mrp.mapper.MrpWorkOrderMapper;
import com.erp.mrp.mapper.MrpWorkReportMapper;
import com.erp.mrp.mapper.MrpWoStepMapper;
import com.erp.mrp.service.MrpWorkReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工序报工服务实现。
 * <p>
 * 单次报工事务：写 mrp_work_report 明细 + 回写 mrp_wo_step 累计数与状态
 * （首个报工 → PROCESSING；累计合格达到计划数量 → FINISHED）。
 * 不合格数记入质量待处理说明（qc_note / qc_pending_note，QC 模块并行开发挂点）。
 * 全部工序完工不阻塞一期完工入库（一期 finish 流程独立，进度以本表报表体现）。
 */
@Service
@RequiredArgsConstructor
public class MrpWorkReportServiceImpl implements MrpWorkReportService {

    private static final DateTimeFormatter NOTE_TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final MrpWorkReportMapper workReportMapper;
    private final MrpWoStepMapper woStepMapper;
    private final MrpWorkOrderMapper workOrderMapper;
    private final MrpShopLookupHelper shopLookup;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long report(WorkReportForm form) {
        MrpWoStep step = requireStep(form.getWoStepId());
        if (MrpShopConstants.STEP_FINISHED.equals(step.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.operation-done-no-report");
        }
        BigDecimal good = form.getGoodQty();
        BigDecimal bad = form.getBadQty();
        if (good.add(bad).signum() <= 0) {
            throw BusinessException.ofKey("erp.mrp.qualified-unqualified-not-both-zero");
        }
        BigDecimal newFinish = step.getFinishQty().add(good);
        if (newFinish.compareTo(step.getPlanQty()) > 0) {
            throw BusinessException.ofKey("erp.mrp.cumulative-qualified-over-plan", newFinish.stripTrailingZeros().toPlainString(), step.getPlanQty().stripTrailingZeros().toPlainString());
        }
        MrpWorkOrder order = workOrderMapper.selectById(step.getWorkOrderId());
        // 报工明细（不合格 > 0 时强制留痕质量待处理说明，QC 挂点）
        MrpWorkReport report = new MrpWorkReport();
        report.setWoStepId(step.getId());
        report.setWorkOrderId(step.getWorkOrderId());
        report.setOperationId(step.getOperationId());
        report.setEmployee(form.getEmployee().trim());
        report.setGoodQty(good);
        report.setBadQty(bad);
        report.setWorkMinutes(form.getWorkMinutes() == null ? 0 : form.getWorkMinutes());
        report.setQcNote(buildQcNote(form, bad));
        report.setReportTime(LocalDateTime.now());
        workReportMapper.insert(report);

        // 回写工序任务：累计合格/不合格/实际工时 + 状态推进
        MrpWoStep upd = new MrpWoStep();
        upd.setId(step.getId());
        upd.setFinishQty(newFinish);
        upd.setBadQty(step.getBadQty().add(bad));
        upd.setActualMinutes(step.getActualMinutes() + report.getWorkMinutes());
        if (newFinish.compareTo(step.getPlanQty()) >= 0) {
            upd.setStatus(MrpShopConstants.STEP_FINISHED);
        } else {
            upd.setStatus(MrpShopConstants.STEP_PROCESSING);
        }
        if (bad.signum() > 0) {
            upd.setQcPendingNote(appendPendingNote(step.getQcPendingNote(),
                    report.getReportTime(), report.getEmployee(), bad));
        }
        woStepMapper.updateById(upd);
        return report.getId();
    }

    @Override
    public List<WorkReportVO> list(Long woStepId, Long workOrderId) {
        List<MrpWorkReport> reports = workReportMapper.selectList(
                Wrappers.lambdaQuery(MrpWorkReport.class)
                        .eq(woStepId != null, MrpWorkReport::getWoStepId, woStepId)
                        .eq(workOrderId != null, MrpWorkReport::getWorkOrderId, workOrderId)
                        .orderByDesc(MrpWorkReport::getId)
                        .last("LIMIT 200"));
        if (reports.isEmpty()) {
            return List.of();
        }
        Map<Long, MrpWorkOrder> orders = workOrderMapper.selectBatchIds(
                        reports.stream().map(MrpWorkReport::getWorkOrderId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(MrpWorkOrder::getId, o -> o));
        Map<Long, MrpOperation> operations = shopLookup.operationMap(
                reports.stream().map(MrpWorkReport::getOperationId).distinct().toList());
        return reports.stream().map(r -> {
            WorkReportVO vo = new WorkReportVO();
            vo.setId(r.getId());
            vo.setWoStepId(r.getWoStepId());
            vo.setWorkOrderId(r.getWorkOrderId());
            MrpWorkOrder wo = orders.get(r.getWorkOrderId());
            vo.setOrderNo(wo == null ? null : wo.getOrderNo());
            vo.setOperationId(r.getOperationId());
            MrpOperation op = operations.get(r.getOperationId());
            vo.setOperationName(op == null ? null : op.getName());
            vo.setEmployee(r.getEmployee());
            vo.setGoodQty(r.getGoodQty());
            vo.setBadQty(r.getBadQty());
            vo.setWorkMinutes(r.getWorkMinutes());
            vo.setQcNote(r.getQcNote());
            vo.setReportTime(r.getReportTime());
            return vo;
        }).toList();
    }

    // ==================== 私有工具 ====================

    private MrpWoStep requireStep(Long id) {
        MrpWoStep step = woStepMapper.selectById(id);
        if (step == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.operation-task-not-exists", id);
        }
        return step;
    }

    /** QC 挂点：不合格 > 0 时生成质量待处理说明（表单说明优先，缺省自动生成摘要） */
    private String buildQcNote(WorkReportForm form, BigDecimal bad) {
        if (bad.signum() <= 0) {
            return form.getQcNote();
        }
        if (form.getQcNote() != null && !form.getQcNote().isBlank()) {
            return form.getQcNote().trim();
        }
        return "待处理：不合格 " + bad.stripTrailingZeros().toPlainString() + " 件（QC模块待接入）";
    }

    /** 累计质量待处理说明（追加式，超长截断到500字符） */
    private String appendPendingNote(String existing, LocalDateTime time, String employee, BigDecimal bad) {
        String entry = time.format(NOTE_TIME_FMT) + " " + employee
                + " 报不合格" + bad.stripTrailingZeros().toPlainString() + "件";
        String merged = existing == null || existing.isBlank() ? entry : existing + "；" + entry;
        return merged.length() > 500 ? merged.substring(0, 500) : merged;
    }
}
