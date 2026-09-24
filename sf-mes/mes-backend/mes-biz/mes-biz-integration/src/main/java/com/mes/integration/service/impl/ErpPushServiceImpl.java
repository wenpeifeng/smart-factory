package com.mes.integration.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.common.exception.BusinessException;
import com.mes.integration.config.IntegrationProperties;
import com.mes.integration.erp.ErpClient;
import com.mes.integration.erp.dto.ErpPickingDTO;
import com.mes.integration.erp.dto.ErpOrderStatusDTO;
import com.mes.integration.erp.dto.ErpQualityResultDTO;
import com.mes.integration.erp.dto.ErpReturnDTO;
import com.mes.integration.erp.dto.ErpWorkReportDTO;
import com.mes.integration.erp.push.ErpPushLog;
import com.mes.integration.mapper.ErpPushLogMapper;
import com.mes.integration.service.ErpPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ERP 回传推送服务实现：推送日志 + 幂等 + 状态回写。
 * <p>设计要点：
 * <ul>
 *   <li>幂等：biz_type + biz_id 唯一索引兜底，先查 SUCCESS 直接返回；</li>
 *   <li>推送失败只置 FAILED 不抛异常（业务主流程不因回传中断），交由重试任务补偿；</li>
 *   <li>重试：{@link ErpPushRetryJob} 定时捞 FAILED 且未超限的记录重推。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpPushServiceImpl implements ErpPushService {

    /** 业务类型常量（int_push_log.biz_type） */
    public static final String BIZ_WORK_REPORT = "WORK_REPORT";
    public static final String BIZ_PICKING = "PICKING";
    public static final String BIZ_RETURN = "RETURN";
    public static final String BIZ_QUALITY = "QUALITY";
    public static final String BIZ_ORDER_STATUS = "ORDER_STATUS";

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final ErpPushLogMapper pushLogMapper;
    private final ErpClient erpClient;
    private final IntegrationProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pushWorkReport(Long recordId, ErpWorkReportDTO dto) {
        push(BIZ_WORK_REPORT, recordId, dto.getWoNo(), dto, () -> erpClient.pushWorkReport(dto));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pushPicking(Long pickingId, ErpPickingDTO dto) {
        push(BIZ_PICKING, pickingId, dto.getPickNo(), dto, () -> erpClient.pushPicking(dto));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pushReturn(Long returnId, ErpReturnDTO dto) {
        push(BIZ_RETURN, returnId, dto.getReturnNo(), dto, () -> erpClient.pushReturn(dto));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pushQualityResult(Long inspectId, ErpQualityResultDTO dto) {
        push(BIZ_QUALITY, inspectId, dto.getInspectNo(), dto, () -> erpClient.pushQualityResult(dto));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pushOrderStatus(Long workorderId, ErpOrderStatusDTO dto) {
        push(BIZ_ORDER_STATUS, workorderId, dto.getWoNo() + ":" + dto.getMesStatus(), dto,
                () -> erpClient.pushOrderStatus(dto));
    }

    /**
     * 通用推送：查重 -> 落 PENDING 日志 -> 调客户端 -> 按结果回写 SUCCESS/FAILED。
     *
     * @param bizType 业务类型
     * @param bizId   业务单据 id（幂等键）
     * @param bizNo   业务单据号
     * @param payload 回传 DTO（序列化为 JSON 快照）
     * @param action  实际客户端调用
     */
    private void push(String bizType, Long bizId, String bizNo, Object payload, Runnable action) {
        Long existed = pushLogMapper.selectCount(Wrappers.lambdaQuery(ErpPushLog.class)
                .eq(ErpPushLog::getBizType, bizType)
                .eq(ErpPushLog::getBizId, bizId)
                .eq(ErpPushLog::getStatus, "SUCCESS"));
        if (existed != null && existed > 0) {
            log.info("[ERP-PUSH] 已成功推送，幂等跳过: {}/{}/{}", bizType, bizId, bizNo);
            return;
        }
        ErpPushLog logEntry = new ErpPushLog();
        logEntry.setBizType(bizType);
        logEntry.setBizId(bizId);
        logEntry.setBizNo(bizNo);
        logEntry.setPayload(toJson(payload));
        logEntry.setStatus("PENDING");
        logEntry.setRetryCount(0);
        logEntry.setMaxRetry(5);
        pushLogMapper.insert(logEntry);
        doPush(logEntry, action);
    }

    /**
     * 执行一次推送并回写状态（日志已持久化，仅状态更新）。
     */
    private void doPush(ErpPushLog logEntry, Runnable action) {
        logEntry.setPushTime(LocalDateTime.now());
        try {
            action.run();
            logEntry.setStatus("SUCCESS");
            logEntry.setSuccessTime(LocalDateTime.now());
            logEntry.setLastError(null);
        } catch (Exception ex) {
            log.warn("[ERP-PUSH] 推送失败待重试: {} {} - {}", logEntry.getBizType(), logEntry.getBizNo(), ex.getMessage());
            logEntry.setStatus("FAILED");
            logEntry.setLastError(truncate(ex.getMessage()));
        }
        pushLogMapper.updateById(logEntry);
    }

    /**
     * 重试失败记录（由定时任务调用）。重新反序列化报文并按类型分派到客户端。
     *
     * @return 本次实际重试的记录数
     */
    public int retryFailed() {
        if (!properties.getErp().isRetryEnabled()) {
            return 0;
        }
        List<ErpPushLog> failed = pushLogMapper.selectList(Wrappers.lambdaQuery(ErpPushLog.class)
                .eq(ErpPushLog::getStatus, "FAILED")
                .apply("retry_count < max_retry")
                .orderByAsc(ErpPushLog::getId)
                .last("LIMIT 50"));
        for (ErpPushLog entry : failed) {
            entry.setRetryCount(entry.getRetryCount() + 1);
            try {
                dispatch(entry);
                entry.setStatus("SUCCESS");
                entry.setSuccessTime(LocalDateTime.now());
                entry.setLastError(null);
            } catch (Exception ex) {
                entry.setStatus("FAILED");
                entry.setLastError(truncate(ex.getMessage()));
            }
            entry.setPushTime(LocalDateTime.now());
            pushLogMapper.updateById(entry);
        }
        return failed.size();
    }

    /** 按业务类型把日志中的 JSON 报文还原为 DTO 并调用客户端 */
    private void dispatch(ErpPushLog entry) {
        String json = entry.getPayload();
        try {
            switch (entry.getBizType()) {
                case BIZ_WORK_REPORT -> erpClient.pushWorkReport(MAPPER.readValue(json, ErpWorkReportDTO.class));
                case BIZ_PICKING -> erpClient.pushPicking(MAPPER.readValue(json, ErpPickingDTO.class));
                case BIZ_RETURN -> erpClient.pushReturn(MAPPER.readValue(json, ErpReturnDTO.class));
                case BIZ_QUALITY -> erpClient.pushQualityResult(MAPPER.readValue(json, ErpQualityResultDTO.class));
                case BIZ_ORDER_STATUS -> erpClient.pushOrderStatus(MAPPER.readValue(json, ErpOrderStatusDTO.class));
                default -> throw new BusinessException("未知的回传业务类型: %s", entry.getBizType());
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("回传报文还原失败: %s", e.getMessage());
        }
    }

    private String toJson(Object payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BusinessException("回传报文序列化失败: %s", e.getMessage());
        }
    }

    private String truncate(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() > 900 ? msg.substring(0, 900) : msg;
    }
}
