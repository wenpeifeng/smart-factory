package com.erp.mrp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.i18n.MessageKeys;
import com.erp.cost.service.CostService;
import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.constant.OutStrategy;
import com.erp.inventory.service.StockService;
import com.erp.mrp.constant.MrpConstants;
import com.erp.mrp.entity.MrpBomItem;
import com.erp.mrp.entity.MrpBomSubstitute;
import com.erp.mrp.entity.MrpMaterialIssue;
import com.erp.mrp.entity.MrpMaterialIssueItem;
import com.erp.mrp.entity.MrpWorkOrder;
import com.erp.mrp.mapper.MrpBomItemMapper;
import com.erp.mrp.mapper.MrpBomSubstituteMapper;
import com.erp.mrp.mapper.MrpMaterialIssueItemMapper;
import com.erp.mrp.mapper.MrpMaterialIssueMapper;
import com.erp.mrp.service.MrpBackflushService;
import com.erp.mrp.service.SeqNoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 倒冲领料服务实现（V621）。
 * <p>
 * 单次完工一个事务内完成：
 * <ol>
 *   <li>按 BOM 子件计算本次应耗 = qty × 完工良品数 × (1+损耗率/100)；</li>
 *   <li>主料库存优先扣减（FIFO/PROD_USE，经 StockService 唯一入口），
 *       不足部分按替代组 priority 升序换算为替代料数量继续补扣
 *       （替代可扣量 = 替代料可用库存 / ratio，折回主料单位）；</li>
 *   <li>全部替代耗尽仍不足 → 抛异常整体回滚（不允许负库存，与领料审核一致）；</li>
 *   <li>落倒冲领料单（issue_type=BACKFLUSH，直接 AUDITED）与明细
 *       （替代料行记录 main_material_id），同事务归集成本（CostService.onStockOut）。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MrpBackflushServiceImpl implements MrpBackflushService {

    /** 成本模块约定的业务类型编码（与 inventory InvBizType.PROD_USE 一致） */
    private static final String COST_BIZ_PROD_USE = "PROD_USE";

    private final MrpBomItemMapper bomItemMapper;
    private final MrpBomSubstituteMapper substituteMapper;
    private final MrpMaterialIssueMapper issueMapper;
    private final MrpMaterialIssueItemMapper issueItemMapper;
    private final SeqNoService seqNoService;
    private final StockService stockService;
    private final CostService costService;

    @Override
    public Long backflush(MrpWorkOrder wo, BigDecimal goodQty, Long warehouseId) {
        List<MrpBomItem> bomItems = bomItemMapper.selectList(
                Wrappers.lambdaQuery(MrpBomItem.class).eq(MrpBomItem::getBomId, wo.getBomId()));
        if (bomItems.isEmpty()) {
            throw BusinessException.ofKey("erp.mrp.bom-no-components-no-backflush", wo.getBomId());
        }
        Map<Long, List<MrpBomSubstitute>> subMap = loadSubstitutes(bomItems);

        // 先落单头（拿到单号用于流水追溯），明细逐行出库后补充
        MrpMaterialIssue issue = new MrpMaterialIssue();
        issue.setIssueNo(seqNoService.next(MrpConstants.PREFIX_ISSUE));
        issue.setWorkOrderId(wo.getId());
        issue.setWarehouseId(warehouseId);
        issue.setIssueType(MrpConstants.ISSUE_TYPE_BACKFLUSH);
        issue.setStatus(MrpConstants.ISSUE_AUDITED);
        issue.setRemark("完工倒冲 " + wo.getOrderNo());
        issueMapper.insert(issue);

        for (MrpBomItem item : bomItems) {
            BigDecimal lossRate = item.getLossRate() == null ? BigDecimal.ZERO : item.getLossRate();
            BigDecimal need = item.getQty().multiply(goodQty)
                    .multiply(BigDecimal.ONE.add(lossRate.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    .setScale(6, RoundingMode.HALF_UP);
            // 主料优先 + 替代补足（不足抛异常整体回滚）
            deductWithSubstitutes(issue, item, need,
                    subMap.getOrDefault(item.getId(), List.of()), warehouseId, wo);
        }
        log.info("[生产订单][完工倒冲] 订单={} 完工良品={} 扣料仓库={} 倒冲领料单={}",
                wo.getOrderNo(), goodQty.stripTrailingZeros().toPlainString(),
                warehouseId, issue.getIssueNo());
        return issue.getId();
    }

    /** 主料优先 + 替代料按优先级补足的一次子件扣料（明细行随扣随落库） */
    private void deductWithSubstitutes(MrpMaterialIssue issue, MrpBomItem item, BigDecimal need,
                                             List<MrpBomSubstitute> substitutes,
                                             Long warehouseId, MrpWorkOrder wo) {
        // 1) 主料可扣 = min(需求, 现存)
        BigDecimal mainAvail = stockService.getAvailableQty(warehouseId, item.getMaterialId());
        BigDecimal mainUse = need.min(mainAvail).max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP);
        if (mainUse.signum() > 0) {
            stockOutAndRecord(issue, warehouseId, item.getMaterialId(), null, mainUse, wo);
        }
        BigDecimal remain = need.subtract(mainUse);
        // 2) 替代料按优先级补足（换算回主料单位计量缺口）
        for (MrpBomSubstitute sub : substitutes) {
            if (remain.signum() <= 0) {
                break;
            }
            BigDecimal ratio = sub.getRatio() == null ? BigDecimal.ONE : sub.getRatio();
            BigDecimal subAvail = stockService.getAvailableQty(warehouseId, sub.getSubMaterialId());
            // 替代料库存能折算回的主料单位
            BigDecimal coverMain = subAvail.divide(ratio, 6, RoundingMode.HALF_UP);
            BigDecimal useMain = remain.min(coverMain);
            if (useMain.signum() <= 0) {
                continue;
            }
            BigDecimal subUse = useMain.multiply(ratio).setScale(6, RoundingMode.HALF_UP);
            stockOutAndRecord(issue, warehouseId, sub.getSubMaterialId(), item.getMaterialId(), subUse, wo);
            remain = remain.subtract(useMain).setScale(6, RoundingMode.HALF_UP);
        }
        if (remain.signum() > 0) {
            throw BusinessException.ofKey(com.erp.common.result.ErrorCode.BAD_REQUEST,
                    MessageKeys.MRP_SUB_STOCK_NOT_ENOUGH,
                    item.getMaterialId(), remain.stripTrailingZeros().toPlainString());
        }
    }

    /** 单物料出库（FIFO/PROD_USE）+ 成本归集 + 倒冲明细行落库 */
    private void stockOutAndRecord(MrpMaterialIssue issue, Long warehouseId, Long materialId,
                                   Long mainMaterialId, BigDecimal qty, MrpWorkOrder wo) {
        stockService.stockOut(warehouseId, materialId, qty, OutStrategy.FIFO, null,
                InvBizType.PROD_USE, issue.getId(), issue.getIssueNo(),
                "完工倒冲领料 " + wo.getOrderNo());
        costService.onStockOut(materialId, warehouseId, COST_BIZ_PROD_USE,
                issue.getIssueNo(), issue.getId(), qty, null);
        MrpMaterialIssueItem row = new MrpMaterialIssueItem();
        row.setIssueId(issue.getId());
        row.setMaterialId(materialId);
        row.setMainMaterialId(mainMaterialId);
        row.setShouldQty(qty);
        row.setActualQty(qty);
        issueItemMapper.insert(row);
    }

    private Map<Long, List<MrpBomSubstitute>> loadSubstitutes(List<MrpBomItem> bomItems) {
        List<Long> itemIds = bomItems.stream().map(MrpBomItem::getId).toList();
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return substituteMapper.selectList(Wrappers.lambdaQuery(MrpBomSubstitute.class)
                        .in(MrpBomSubstitute::getBomItemId, itemIds)
                        .orderByAsc(MrpBomSubstitute::getPriority)
                        .orderByAsc(MrpBomSubstitute::getId))
                .stream().collect(Collectors.groupingBy(MrpBomSubstitute::getBomItemId,
                        Collectors.toCollection(ArrayList::new)));
    }
}
