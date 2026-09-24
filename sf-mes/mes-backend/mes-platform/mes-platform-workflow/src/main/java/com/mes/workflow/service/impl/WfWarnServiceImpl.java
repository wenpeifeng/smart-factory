package com.mes.workflow.service.impl;

import com.mes.workflow.constant.WfConstants;
import com.mes.workflow.dto.vo.CreditWarnRow;
import com.mes.workflow.dto.vo.OrderWarnRow;
import com.mes.workflow.dto.vo.StockWarnRow;
import com.mes.workflow.entity.WfMessage;
import com.mes.workflow.mapper.WfRefMapper;
import com.mes.workflow.service.WfMessageService;
import com.mes.workflow.service.WfWarnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 预警任务服务实现。
 * <p>
 * 三类扫描（库存安全线/客户信用/生产超期，SQL 见 WfRefMapper）→
 * Redis SETNX（key=mes:wf:warn:{类型}:{业务键}，TTL 24h）去重 →
 * 给管理员（id=1）发 WARN 消息。
 * <p>
 * 去重语义：24h 内同一业务对象只提醒一次，下一次最早在 key 过期后的
 * 下一轮扫描再发 —— 满足"每小时扫描 + 同一预警 24h 去重"的要求。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfWarnServiceImpl implements WfWarnService {

    /** 预警类型标识（Redis key 与文案共用） */
    private static final String TYPE_STOCK = "stock";
    private static final String TYPE_CREDIT = "credit";
    private static final String TYPE_ORDER = "order";

    private final WfRefMapper refMapper;
    private final WfMessageService messageService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public int scanOnce() {
        int sent = 0;
        try {
            sent += scanSafetyStock();
            sent += scanCreditExceeded();
            sent += scanOverdueWorkOrders();
        } catch (Exception e) {
            // 预警属旁路能力：扫描异常只记日志，不影响主流程与调度线程
            log.error("预警扫描执行失败: {}", e.getMessage(), e);
        }
        return sent;
    }

    /** 库存低于安全库存预警 */
    private int scanSafetyStock() {
        List<StockWarnRow> rows = refMapper.scanSafetyStock();
        int sent = 0;
        for (StockWarnRow row : rows) {
            String bizKey = TYPE_STOCK + ":" + row.getMaterialId();
            if (acquireDedup(bizKey)) {
                messageService.push(WfConstants.WARN_RECEIVER_ADMIN, WfMessage.TYPE_WARN,
                        "库存预警：" + row.getMaterialName(),
                        "物料[" + row.getMaterialCode() + " " + row.getMaterialName()
                                + "]现存量 " + row.getTotalQty().stripTrailingZeros().toPlainString()
                                + " 已低于安全库存 " + row.getSafetyStock().stripTrailingZeros().toPlainString()
                                + "，请及时补货",
                        "/inventory/stock");
                sent++;
            }
        }
        log.debug("库存安全库存扫描：命中 {} 条，新发预警 {} 条", rows.size(), sent);
        return sent;
    }

    /** 客户信用超限预警 */
    private int scanCreditExceeded() {
        List<CreditWarnRow> rows = refMapper.scanCreditExceeded();
        int sent = 0;
        for (CreditWarnRow row : rows) {
            String bizKey = TYPE_CREDIT + ":" + row.getCustomerId();
            if (acquireDedup(bizKey)) {
                messageService.push(WfConstants.WARN_RECEIVER_ADMIN, WfMessage.TYPE_WARN,
                        "信用预警：" + row.getCustomerName(),
                        "客户[" + row.getCustomerCode() + " " + row.getCustomerName()
                                + "]未收回应收 " + row.getOutstandingAmount().toPlainString()
                                + " 已超信用额度 " + row.getCreditLimit().toPlainString()
                                + "，请控制赊销并催收",
                        "/finance/receivables");
                sent++;
            }
        }
        log.debug("客户信用扫描：命中 {} 条，新发预警 {} 条", rows.size(), sent);
        return sent;
    }

    /** 生产订单计划结束超期预警 */
    private int scanOverdueWorkOrders() {
        List<OrderWarnRow> rows = refMapper.scanOverdueWorkOrders();
        int sent = 0;
        for (OrderWarnRow row : rows) {
            String bizKey = TYPE_ORDER + ":" + row.getOrderId();
            if (acquireDedup(bizKey)) {
                messageService.push(WfConstants.WARN_RECEIVER_ADMIN, WfMessage.TYPE_WARN,
                        "生产超期：" + row.getOrderNo(),
                        "生产订单[" + row.getOrderNo() + "]计划结束日期 " + row.getPlanEndDate()
                                + " 已超期仍未完工（状态 " + row.getStatus() + "），请跟进",
                        "/mrp/work-orders");
                sent++;
            }
        }
        log.debug("生产订单超期扫描：命中 {} 条，新发预警 {} 条", rows.size(), sent);
        return sent;
    }

    /**
     * 预警去重：SETNX + TTL 24h。
     *
     * @param bizKey 类型:业务ID（拼在前缀后）
     * @return true=首次（应发消息）；false=24h 内已发过（去重）
     */
    private boolean acquireDedup(String bizKey) {
        String key = WfConstants.WARN_DEDUP_KEY_PREFIX + bizKey;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, String.valueOf(System.currentTimeMillis()), Duration.ofHours(24));
        return Boolean.TRUE.equals(acquired);
    }
}
