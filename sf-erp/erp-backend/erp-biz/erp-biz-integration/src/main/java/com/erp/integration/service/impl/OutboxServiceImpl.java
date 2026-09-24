package com.erp.integration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.i18n.MessageKeys;
import com.erp.integration.config.IntegrationProperties;
import com.erp.integration.dto.MesBomDTO;
import com.erp.integration.dto.MesMaterialDTO;
import com.erp.integration.dto.MesProductionOrderDTO;
import com.erp.integration.dto.MesRoutingDTO;
import com.erp.integration.entity.IntOutbox;
import com.erp.integration.mapper.IntOutboxMapper;
import com.erp.integration.mes.MesClient;
import com.erp.integration.service.OutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 出站发件箱服务实现。
 * <p>设计要点：
 * <ul>
 *   <li>入队：落 PENDING + 报文 JSON 快照后立即投递一次（事务外副作用由状态列承载）；</li>
 *   <li>退避：失败按 1m/5m/30m/2h/6h 五档指数退避，第 5 次失败置 DEAD 死信人工处理；</li>
 *   <li>stub 模式客户端恒成功，outbox 即 SUCCESS（mock 留痕），链路可先行联调。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    /** 指数退避表（分钟）：第 N 次失败后等待 RETRY_DELAYS[min(retryCount-1, len-1)] 再试 */
    static final long[] RETRY_DELAY_MINUTES = {1, 5, 30, 120, 360};

    /** 最大重试次数（超过置 DEAD） */
    static final int MAX_RETRY = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final IntOutboxMapper outboxMapper;
    private final MesClient mesClient;
    private final IntegrationProperties properties;

    @Override
    public void enqueue(String bizType, Long bizId, Object payload) {
        IntOutbox entry = new IntOutbox();
        entry.setBizType(bizType);
        entry.setBizId(bizId);
        entry.setEventTime(LocalDateTime.now());
        entry.setPayload(toJson(payload));
        entry.setStatus("PENDING");
        entry.setRetryCount(0);
        entry.setNextRetryTime(LocalDateTime.now());
        outboxMapper.insert(entry);
        // 立即投递一次：失败仅记状态退避重试，不阻断业务主流程
        dispatch(entry);
    }

    @Override
    public int retryDue() {
        if (!properties.getMes().isRetryEnabled()) {
            return 0;
        }
        List<IntOutbox> due = outboxMapper.selectList(new LambdaQueryWrapper<IntOutbox>()
                .eq(IntOutbox::getStatus, "PENDING")
                .le(IntOutbox::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(IntOutbox::getId)
                .last("LIMIT 50"));
        for (IntOutbox entry : due) {
            dispatch(entry);
        }
        return due.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void manualRetry(Long id) {
        IntOutbox entry = outboxMapper.selectById(id);
        if (entry == null) {
            throw BusinessException.ofKey(MessageKeys.BIZ_DATA_NOT_EXISTS, id);
        }
        if ("SUCCESS".equals(entry.getStatus())) {
            throw BusinessException.ofKey("erp.integration.outbox-already-success");
        }
        dispatch(entry);
    }

    @Override
    public IPage<IntOutbox> page(long pageNum, long pageSize, String status, String bizType) {
        return outboxMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<IntOutbox>()
                .eq(status != null && !status.isBlank(), IntOutbox::getStatus, status)
                .eq(bizType != null && !bizType.isBlank(), IntOutbox::getBizType, bizType)
                .orderByDesc(IntOutbox::getId));
    }

    /**
     * 执行一次投递并回写状态：成功置 SUCCESS；失败置 PENDING（未超限，按退避表顺延）
     * 或 DEAD（第 {@value #MAX_RETRY} 次失败后死信）。
     */
    private void dispatch(IntOutbox entry) {
        try {
            doPush(entry);
            entry.setStatus("SUCCESS");
            entry.setSuccessTime(LocalDateTime.now());
            entry.setError(null);
        } catch (Exception ex) {
            int attempts = entry.getRetryCount() == null ? 0 : entry.getRetryCount() + 1;
            entry.setRetryCount(attempts);
            if (attempts >= MAX_RETRY) {
                entry.setStatus("DEAD");
                log.warn("[OUTBOX] 死信: {}/{} 重试{}次仍失败 - {}",
                        entry.getBizType(), entry.getBizId(), attempts, ex.getMessage());
            } else {
                entry.setStatus("PENDING");
                entry.setNextRetryTime(LocalDateTime.now().plusMinutes(
                        RETRY_DELAY_MINUTES[Math.min(attempts - 1 < 0 ? 0 : attempts - 1, RETRY_DELAY_MINUTES.length - 1)]));
                log.warn("[OUTBOX] 投递失败待重试(第{}次): {}/{} - {}",
                        attempts, entry.getBizType(), entry.getBizId(), ex.getMessage());
            }
            entry.setError(truncate(ex.getMessage()));
        }
        outboxMapper.updateById(entry);
    }

    /** 按业务类型反序列化报文并分派到 MES 客户端 */
    private void doPush(IntOutbox entry) throws Exception {
        String payload = entry.getPayload();
        switch (entry.getBizType()) {
            case BIZ_PRODUCTION_ORDER ->
                    mesClient.pushProductionOrder(MAPPER.readValue(payload, MesProductionOrderDTO.class));
            case BIZ_MATERIAL -> mesClient.pushMaterial(MAPPER.readValue(payload, MesMaterialDTO.class));
            case BIZ_BOM -> mesClient.pushBom(MAPPER.readValue(payload, MesBomDTO.class));
            case BIZ_ROUTING -> mesClient.pushRouting(MAPPER.readValue(payload, MesRoutingDTO.class));
            default -> throw new IllegalStateException("未知 outbox 业务类型: " + entry.getBizType());
        }
    }

    private String toJson(Object payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("outbox 报文序列化失败", ex);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
