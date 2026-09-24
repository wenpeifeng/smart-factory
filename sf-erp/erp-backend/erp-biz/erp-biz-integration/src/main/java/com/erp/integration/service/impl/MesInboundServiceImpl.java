package com.erp.integration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.basedata.entity.BaseMaterial;
import com.erp.basedata.entity.BaseWarehouse;
import com.erp.basedata.mapper.BaseMaterialMapper;
import com.erp.basedata.mapper.BaseWarehouseMapper;
import com.erp.common.exception.BusinessException;
import com.erp.integration.dto.MesOrderStatusDTO;
import com.erp.integration.dto.MesPickingDTO;
import com.erp.integration.dto.MesQualityResultDTO;
import com.erp.integration.dto.MesReturnDTO;
import com.erp.integration.dto.MesWorkReportDTO;
import com.erp.integration.entity.IntMessageLog;
import com.erp.integration.mapper.IntMessageLogMapper;
import com.erp.integration.service.MesInboundService;
import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.constant.OutStrategy;
import com.erp.inventory.service.StockService;
import com.erp.mrp.constant.MrpConstants;
import com.erp.mrp.entity.MrpWorkOrder;
import com.erp.mrp.mapper.MrpWorkOrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MES 入站服务实现。
 * <p>幂等口径：先查 bizType+bizKey 已 SUCCESS 直接跳过（兜住 DTO 无稳定事件时间的场景），
 * 再以 (bizType, bizKey, eventTime) 唯一键 insert 占位防并发重放（DuplicateKeyException 视为重复）；
 * 占位与业务处理同一事务，业务失败整体回滚不留脏日志。
 * <p>质检分支（契约 #6 最小实现）：结论合格且 FQC 且来源可关联 ERP 工单时，
 * 按合格数量走 PROD_IN 产成品入库（MES 侧约定报工与 FQC 回传二选一驱动入库，避免双计）；
 * 结论不合格仅落日志，实物处理由退料/红冲回传承担。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesInboundServiceImpl implements MesInboundService {

    /** 业务类型常量（int_message_log.biz_type） */
    public static final String BIZ_WORK_REPORT = "WORK_REPORT";
    public static final String BIZ_PICKING = "PICKING";
    public static final String BIZ_RETURN = "RETURN";
    public static final String BIZ_QUALITY = "QUALITY";
    public static final String BIZ_ORDER_STATUS = "ORDER_STATUS";

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final IntMessageLogMapper messageLogMapper;
    private final MrpWorkOrderMapper workOrderMapper;
    private final BaseMaterialMapper materialMapper;
    private final BaseWarehouseMapper warehouseMapper;
    private final StockService stockService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String receiveWorkReport(MesWorkReportDTO dto) {
        String bizKey = dto.getWoNo() + "|" + (dto.getOperationName() == null || dto.getOperationName().isBlank()
                ? "SUMMARY" : dto.getOperationName());
        return accept(BIZ_WORK_REPORT, bizKey,
                dto.getReportTime() == null ? LocalDateTime.now() : dto.getReportTime(), dto, () -> {
                    MrpWorkOrder wo = requireWorkOrder(dto.getErpOrderNo(), dto.getWoNo());
                    BigDecimal good = nvl(dto.getGoodQty());
                    BigDecimal scrap = nvl(dto.getScrapQty());
                    // 良品入库：走工单默认入库仓库，PROD_IN 流水（凭证引擎联动由现有链路承接）
                    if (good.signum() > 0) {
                        if (wo.getWarehouseId() == null) {
                            throw BusinessException.ofKey("erp.integration.wo-no-warehouse", wo.getOrderNo());
                        }
                        stockService.stockIn(wo.getWarehouseId(), wo.getMaterialId(), null, good,
                                InvBizType.PROD_IN, wo.getId(), wo.getOrderNo(),
                                "MES完工报工入库 " + dto.getWoNo());
                    }
                    // 回写工单累计：finish_qty 累计良品、bad_qty 累计报废，
                    // 状态镜像与 ERP 手工完工同口径（累计达量 FINISHED，否则 PRODUCING）
                    MrpWorkOrder upd = new MrpWorkOrder();
                    upd.setId(wo.getId());
                    upd.setFinishQty(wo.getFinishQty().add(good));
                    upd.setBadQty(wo.getBadQty().add(scrap));
                    BigDecimal total = wo.getFinishQty().add(wo.getBadQty()).add(good).add(scrap);
                    upd.setStatus(total.compareTo(wo.getQty()) >= 0
                            ? MrpConstants.WO_FINISHED : MrpConstants.WO_PRODUCING);
                    upd.setMesStatus("FINISHING");
                    workOrderMapper.updateById(upd);
                    return "完工报工处理成功: 良品 " + good.stripTrailingZeros().toPlainString()
                            + "，报废 " + scrap.stripTrailingZeros().toPlainString();
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String receivePicking(MesPickingDTO dto) {
        return accept(BIZ_PICKING, dto.getPickNo(), LocalDateTime.now(), dto, () -> {
            Long warehouseId = requireWarehouseId(dto.getWarehouseCode());
            for (MesPickingDTO.Item item : dto.getItems()) {
                BaseMaterial material = requireMaterial(item.getMaterialCode());
                // 批次物料带批次走 BATCH 精确扣减，未指定批次走默认策略（FIFO/AVG 由 MES 配置口径决定）
                OutStrategy strategy = item.getBatchNo() == null || item.getBatchNo().isBlank()
                        ? null : OutStrategy.BATCH;
                stockService.stockOut(warehouseId, material.getId(), item.getQty(), strategy,
                        item.getBatchNo(), InvBizType.PROD_USE, null, dto.getPickNo(),
                        "MES领料出库 " + dto.getPickNo());
            }
            return "领料出库完成: " + dto.getItems().size() + " 行";
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String receiveReturn(MesReturnDTO dto) {
        return accept(BIZ_RETURN, dto.getReturnNo(), LocalDateTime.now(), dto, () -> {
            Long warehouseId = requireWarehouseId(dto.getWarehouseCode());
            for (MesReturnDTO.Item item : dto.getItems()) {
                BaseMaterial material = requireMaterial(item.getMaterialCode());
                // 退料 = 生产领料的反向入库：OTH_IN 冲销入账（流水单号挂退料单号可追溯）
                stockService.stockIn(warehouseId, material.getId(), item.getBatchNo(), item.getQty(),
                        InvBizType.OTH_IN, null, dto.getReturnNo(),
                        "MES退料入库 " + dto.getReturnNo());
            }
            return "退料入库完成: " + dto.getItems().size() + " 行";
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String receiveQualityResult(MesQualityResultDTO dto) {
        LocalDateTime eventTime = dto.getInspectDate() == null ? LocalDateTime.now()
                : dto.getInspectDate().atStartOfDay();
        return accept(BIZ_QUALITY, dto.getInspectNo(), eventTime, dto, () -> {
            if (dto.getConclusion() != null && dto.getConclusion() == 1
                    && "FQC".equalsIgnoreCase(dto.getInspectType())
                    && dto.getQualifiedQty() != null && dto.getQualifiedQty().signum() > 0) {
                MrpWorkOrder wo = requireWorkOrder(null, dto.getSourceNo());
                if (wo.getWarehouseId() == null) {
                    throw BusinessException.ofKey("erp.integration.wo-no-warehouse", wo.getOrderNo());
                }
                stockService.stockIn(wo.getWarehouseId(), wo.getMaterialId(), dto.getBatchNo(),
                        dto.getQualifiedQty(), InvBizType.PROD_IN, wo.getId(), wo.getOrderNo(),
                        "MES质检合格入库 " + dto.getInspectNo());
                return "FQC 合格入库完成: " + dto.getQualifiedQty().stripTrailingZeros().toPlainString();
            }
            // 不合格 / 非成品检验：仅记录结论，实物动账由退料或红冲回传承担
            return "质检结果已记录: 结论=" + dto.getConclusion();
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String receiveOrderStatus(MesOrderStatusDTO dto) {
        LocalDateTime eventTime = dto.getEventTime() == null ? LocalDateTime.now() : dto.getEventTime();
        return accept(BIZ_ORDER_STATUS, dto.getWoNo() + "|" + dto.getMesStatus(), eventTime, dto, () -> {
            MrpWorkOrder wo = requireWorkOrder(dto.getErpOrderNo(), dto.getWoNo());
            // 仅镜像 MES 执行状态，不改 ERP 状态机语义（契约第 1 节：只做状态镜像）
            MrpWorkOrder upd = new MrpWorkOrder();
            upd.setId(wo.getId());
            upd.setMesStatus(dto.getMesStatus());
            workOrderMapper.updateById(upd);
            return "工单进度镜像已更新: " + dto.getMesStatus();
        });
    }

    @Override
    public IPage<IntMessageLog> pageLogs(long pageNum, long pageSize, String bizType, String status) {
        return messageLogMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<IntMessageLog>()
                .eq(bizType != null && !bizType.isBlank(), IntMessageLog::getBizType, bizType)
                .eq(status != null && !status.isBlank(), IntMessageLog::getStatus, status)
                .orderByDesc(IntMessageLog::getId));
    }

    // ==================== 内部工具 ====================

    /**
     * 统一幂等入口：占位（唯一键兜底 + bizKey 查重）-> 业务处理 -> 回写结果摘要。
     *
     * @param bizType   业务类型
     * @param bizKey    幂等业务键
     * @param eventTime 事件时间
     * @param payload   原始报文（JSON 快照）
     * @param action    业务处理（同事务）
     * @return 处理结果摘要（重复消息返回重复提示）
     */
    private String accept(String bizType, String bizKey, LocalDateTime eventTime, Object payload,
                          java.util.function.Supplier<String> action) {
        if (alreadyProcessed(bizType, bizKey)) {
            log.info("[MES-IN] 重复消息幂等跳过: {}/{}", bizType, bizKey);
            markDuplicate(bizType, bizKey, eventTime, payload);
            return "重复消息，已幂等跳过";
        }
        IntMessageLog logEntry = new IntMessageLog();
        logEntry.setBizType(bizType);
        logEntry.setBizKey(bizKey);
        logEntry.setEventTime(eventTime);
        logEntry.setPayload(toJson(payload));
        logEntry.setStatus("SUCCESS");
        try {
            messageLogMapper.insert(logEntry);
        } catch (DuplicateKeyException ex) {
            log.info("[MES-IN] 并发重放幂等跳过: {}/{}", bizType, bizKey);
            markDuplicate(bizType, bizKey, eventTime, payload);
            return "重复消息，已幂等跳过";
        }
        String result = action.get();
        logEntry.setError(result);
        messageLogMapper.updateById(logEntry);
        log.info("[MES-IN] 处理成功: {}/{} -> {}", bizType, bizKey, result);
        return result;
    }

    /** bizType+bizKey 已有 SUCCESS 记录则视为已处理（跨 eventTime 的业务级幂等） */
    private boolean alreadyProcessed(String bizType, String bizKey) {
        Long count = messageLogMapper.selectCount(new LambdaQueryWrapper<IntMessageLog>()
                .eq(IntMessageLog::getBizType, bizType)
                .eq(IntMessageLog::getBizKey, bizKey)
                .eq(IntMessageLog::getStatus, "SUCCESS"));
        return count != null && count > 0;
    }

    /** 重复消息留痕（DUPLICATE，不受唯一键约束：eventTime 不同即可插入） */
    private void markDuplicate(String bizType, String bizKey, LocalDateTime eventTime, Object payload) {
        try {
            IntMessageLog dup = new IntMessageLog();
            dup.setBizType(bizType);
            dup.setBizKey(bizKey);
            dup.setEventTime(eventTime);
            dup.setPayload(toJson(payload));
            dup.setStatus("DUPLICATE");
            messageLogMapper.insert(dup);
        } catch (DuplicateKeyException ex) {
            // 完全相同的三元组重放：不再重复留痕
        }
    }

    /** 按 ERP 订单号（优先）或 MES 工单号定位工单 */
    private MrpWorkOrder requireWorkOrder(String erpOrderNo, String woNo) {
        MrpWorkOrder wo = null;
        if (erpOrderNo != null && !erpOrderNo.isBlank()) {
            wo = workOrderMapper.selectOne(new LambdaQueryWrapper<MrpWorkOrder>()
                    .eq(MrpWorkOrder::getOrderNo, erpOrderNo).last("LIMIT 1"));
        }
        if (wo == null && woNo != null && !woNo.isBlank()) {
            wo = workOrderMapper.selectOne(new LambdaQueryWrapper<MrpWorkOrder>()
                    .eq(MrpWorkOrder::getOrderNo, woNo).last("LIMIT 1"));
        }
        if (wo == null) {
            throw BusinessException.ofKey("erp.integration.work-order-not-found",
                    erpOrderNo != null && !erpOrderNo.isBlank() ? erpOrderNo : woNo);
        }
        return wo;
    }

    private Long requireWarehouseId(String warehouseCode) {
        BaseWarehouse wh = warehouseMapper.selectOne(new LambdaQueryWrapper<BaseWarehouse>()
                .eq(BaseWarehouse::getCode, warehouseCode).last("LIMIT 1"));
        if (wh == null) {
            throw BusinessException.ofKey("erp.integration.warehouse-not-found", warehouseCode);
        }
        return wh.getId();
    }

    private BaseMaterial requireMaterial(String materialCode) {
        BaseMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()
                .eq(BaseMaterial::getCode, materialCode).last("LIMIT 1"));
        if (material == null) {
            throw BusinessException.ofKey("erp.integration.material-not-found", materialCode);
        }
        return material;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String toJson(Object payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("入站报文序列化失败", ex);
        }
    }
}
