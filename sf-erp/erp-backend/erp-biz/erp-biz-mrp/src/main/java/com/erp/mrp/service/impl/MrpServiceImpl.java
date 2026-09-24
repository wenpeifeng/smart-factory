package com.erp.mrp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import com.erp.mrp.constant.MrpConstants;
import com.erp.mrp.dto.form.MrpRunForm;
import com.erp.mrp.dto.vo.BatchDetailVO;
import com.erp.mrp.dto.vo.CalcBatchVO;
import com.erp.mrp.dto.vo.CalcDetailTreeVO;
import com.erp.mrp.dto.vo.MaterialBriefVO;
import com.erp.mrp.dto.vo.MaterialQtyVO;
import com.erp.mrp.dto.vo.MrpRunResultVO;
import com.erp.mrp.entity.MrpBom;
import com.erp.mrp.entity.MrpBomItem;
import com.erp.mrp.entity.MrpCalc;
import com.erp.mrp.entity.MrpCalcDetail;
import com.erp.mrp.entity.MrpMps;
import com.erp.mrp.entity.MrpSuggestion;
import com.erp.mrp.mapper.MrpBomItemMapper;
import com.erp.mrp.mapper.MrpCalcDetailMapper;
import com.erp.mrp.mapper.MrpCalcMapper;
import com.erp.mrp.mapper.MrpIntegrationMapper;
import com.erp.mrp.mapper.MrpMpsMapper;
import com.erp.mrp.mapper.MrpSuggestionMapper;
import com.erp.mrp.service.MrpBomService;
import com.erp.mrp.service.MrpService;
import com.erp.mrp.service.SeqNoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MRP 运算实现 —— 逐级展开（BFS）+ 可用库存池扣抵 + 按物料汇总建议。
 * <p>
 * 口径说明：
 * <ul>
 *   <li>现有库存：inv_stock 按物料全仓 SUM（运算时点快照）；</li>
 *   <li>在途：pur_order(status='APPROVED') 未入库完的明细数量 SUM（采购模块未上线时按 0 处理）；</li>
 *   <li>“可用池”按物料在整个运算批次内共享扣抵（多个 MPS / 多分支重复出现同一物料时不会重复抵扣库存）；</li>
 *   <li>建议类型：物料存在有效 BOM → PRODUCE（继续向下展开），否则 → PURCHASE 采购件叶子。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MrpServiceImpl implements MrpService {

    private static final int SCALE = 6;

    private final MrpMpsMapper mpsMapper;
    private final MrpCalcMapper calcMapper;
    private final MrpCalcDetailMapper calcDetailMapper;
    private final MrpSuggestionMapper suggestionMapper;
    private final MrpBomItemMapper bomItemMapper;
    private final MrpBomService bomService;
    private final MrpIntegrationMapper integrationMapper;
    private final MrpLookupHelper lookup;
    private final SeqNoService seqNoService;

    /** BFS 展开节点（携带祖先物料链，用于循环引用检测） */
    private record Node(Long materialId, BigDecimal gross, int level, String path,
                        String parentPath, LocalDate needDate, Set<Long> ancestors) {
    }

    /** 建议汇总累加器（物料+类型维度合并数量，取最早建议日期与最小层级） */
    private static final class SuggestionAgg {
        private BigDecimal qty = BigDecimal.ZERO;
        private LocalDate suggestDate;
        private int level = Integer.MAX_VALUE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MrpRunResultVO run(MrpRunForm form) {
        // 1. 取 MPS 行：显式指定 → 指定行；否则全部“待运算”
        List<MrpMps> mpsList = loadMps(form);
        if (mpsList.isEmpty()) {
            throw BusinessException.ofKey("erp.mrp.no-mps-lines");
        }

        // 2. 校验成品均有有效 BOM，并预扫全部涉及物料（供批量取库存/在途）
        Map<Long, List<MrpBomItem>> bomItemCache = new HashMap<>();
        Set<Long> allMaterialIds = new HashSet<>();
        Map<Long, MaterialBriefVO> materialNames = new HashMap<>();
        for (MrpMps mps : mpsList) {
            MrpBom bom = requireActiveBom(mps.getMaterialId());
            allMaterialIds.add(mps.getMaterialId());
            preScan(bom, bomItemCache, allMaterialIds);
        }
        materialNames.putAll(lookup.materialMap(allMaterialIds));

        // 3. 库存 / 在途汇总（约定表只读；采购模块未上线时在途=0）
        Map<Long, BigDecimal> stockMap = sumMap(integrationMapper.sumStockByMaterial(List.copyOf(allMaterialIds)));
        Map<Long, BigDecimal> onwayMap = tableExists("pur_order") && tableExists("pur_order_detail")
                ? sumMap(integrationMapper.sumOnwayByMaterial(List.copyOf(allMaterialIds)))
                : Map.of();
        // 可用池：库存+在途，批次内共享扣抵
        Map<Long, BigDecimal> pool = new HashMap<>();
        for (Long id : allMaterialIds) {
            pool.put(id, stockMap.getOrDefault(id, BigDecimal.ZERO)
                    .add(onwayMap.getOrDefault(id, BigDecimal.ZERO)));
        }

        // 4. 逐个 MPS 逐级展开
        String batchNo = seqNoService.next(MrpConstants.PREFIX_CALC_BATCH);
        List<MrpCalcDetail> details = new ArrayList<>();
        Map<String, SuggestionAgg> suggestions = new LinkedHashMap<>();
        for (MrpMps mps : mpsList) {
            expandMps(mps, batchNo, pool, stockMap, onwayMap, bomItemCache, details, suggestions);
        }

        // 5. 落库：批次头 → 展开明细 → 建议 → MPS 标记已运算
        MrpCalc calc = new MrpCalc();
        calc.setBatchNo(batchNo);
        calc.setMpsCount(mpsList.size());
        calc.setSuggestionCount(suggestions.size());
        calc.setRemark(form.getRemark());
        calcMapper.insert(calc);
        details.forEach(calcDetailMapper::insert);
        for (var entry : suggestions.entrySet()) {
            String[] key = entry.getKey().split("\\|");
            SuggestionAgg agg = entry.getValue();
            MrpSuggestion sug = new MrpSuggestion();
            sug.setBatchNo(batchNo);
            sug.setPlanType(key[0]);
            sug.setMaterialId(Long.valueOf(key[1]));
            sug.setQty(agg.qty.setScale(4, RoundingMode.HALF_UP));
            sug.setSuggestDate(agg.suggestDate);
            sug.setLevel(agg.level == Integer.MAX_VALUE ? 0 : agg.level);
            sug.setStatus(MrpConstants.SUGGESTION_PENDING);
            suggestionMapper.insert(sug);
        }
        for (MrpMps mps : mpsList) {
            MrpMps upd = new MrpMps();
            upd.setId(mps.getId());
            upd.setStatus(MrpConstants.MPS_CALCULATED);
            mpsMapper.updateById(upd);
        }

        MrpRunResultVO result = new MrpRunResultVO();
        result.setBatchNo(batchNo);
        result.setMpsCount(mpsList.size());
        result.setDetailCount(details.size());
        result.setSuggestionCount(suggestions.size());
        return result;
    }

    @Override
    public PageResult<CalcBatchVO> pageBatches(long pageNum, long pageSize) {
        return PageUtils.of(calcMapper, Wrappers.lambdaQuery(MrpCalc.class)
                        .orderByDesc(MrpCalc::getId), pageNum, pageSize)
                .map(calc -> {
                    CalcBatchVO vo = new CalcBatchVO();
                    vo.setId(calc.getId());
                    vo.setBatchNo(calc.getBatchNo());
                    vo.setMpsCount(calc.getMpsCount());
                    vo.setSuggestionCount(calc.getSuggestionCount());
                    vo.setRemark(calc.getRemark());
                    vo.setCreateTime(calc.getCreateTime());
                    return vo;
                });
    }

    @Override
    public BatchDetailVO batchDetails(String batchNo) {
        List<MrpCalcDetail> details = calcDetailMapper.selectList(
                Wrappers.lambdaQuery(MrpCalcDetail.class)
                        .eq(MrpCalcDetail::getBatchNo, batchNo)
                        .orderByAsc(MrpCalcDetail::getId));
        if (details.isEmpty()) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.run-batch-not-exists", batchNo);
        }
        Map<Long, MaterialBriefVO> materials = lookup.materialMap(
                details.stream().map(MrpCalcDetail::getMaterialId).toList());
        // BFS 落库顺序保证父节点先于子节点出现，一次遍历即可组树
        Map<String, CalcDetailTreeVO> nodes = new HashMap<>();
        List<CalcDetailTreeVO> roots = new ArrayList<>();
        for (MrpCalcDetail d : details) {
            CalcDetailTreeVO node = toTreeNode(d, materials);
            nodes.put(d.getNodePath(), node);
            if (d.getParentPath() == null) {
                roots.add(node);
            } else {
                CalcDetailTreeVO parent = nodes.get(d.getParentPath());
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }
        BatchDetailVO vo = new BatchDetailVO();
        vo.setBatchNo(batchNo);
        vo.setTrees(roots);
        return vo;
    }

    // ==================== 运算核心 ====================

    /**
     * 单个 MPS 的逐级展开。
     *
     * @param pool     可用池（批次内跨 MPS 共享，方法内直接扣减）
     * @param bomItemCache BOM子件缓存（bomId → items，避免同 BOM 重复查库）
     */
    private void expandMps(MrpMps mps, String batchNo, Map<Long, BigDecimal> pool,
                           Map<Long, BigDecimal> stockMap, Map<Long, BigDecimal> onwayMap,
                           Map<Long, List<MrpBomItem>> bomItemCache,
                           List<MrpCalcDetail> details, Map<String, SuggestionAgg> suggestions) {
        Deque<Node> queue = new ArrayDeque<>();
        queue.add(new Node(mps.getMaterialId(), mps.getQty().setScale(SCALE, RoundingMode.HALF_UP),
                0, mps.getMaterialId() + "#0", null, mps.getStartDate(), Set.of()));
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            BigDecimal avail = pool.getOrDefault(node.materialId(), BigDecimal.ZERO).max(BigDecimal.ZERO);
            BigDecimal used = node.gross().min(avail);
            pool.put(node.materialId(), avail.subtract(used));
            BigDecimal net = node.gross().subtract(used).max(BigDecimal.ZERO);

            // 建议类型：有有效 BOM → 生产建议（继续展开），无 → 采购建议（叶子）
            MrpBom bom = bomService.findLatestActive(node.materialId());
            String planType = bom != null ? MrpConstants.PLAN_PRODUCE : MrpConstants.PLAN_PURCHASE;
            boolean hasSuggestion = net.signum() > 0;
            LocalDate suggestDate = hasSuggestion
                    ? node.needDate().minusDays(MrpConstants.LEAD_TIME_DAYS) : null;

            MrpCalcDetail detail = new MrpCalcDetail();
            detail.setBatchNo(batchNo);
            detail.setMpsId(mps.getId());
            detail.setLevel(node.level());
            detail.setMaterialId(node.materialId());
            detail.setGrossQty(node.gross());
            detail.setStockQty(stockMap.getOrDefault(node.materialId(), BigDecimal.ZERO));
            detail.setOnwayQty(onwayMap.getOrDefault(node.materialId(), BigDecimal.ZERO));
            detail.setNetQty(net);
            detail.setSuggestQty(hasSuggestion ? net : null);
            detail.setSuggestDate(suggestDate);
            detail.setPlanType(hasSuggestion ? planType : null);
            detail.setNodePath(node.path());
            detail.setParentPath(node.parentPath());
            details.add(detail);

            if (hasSuggestion) {
                String key = planType + "|" + node.materialId();
                SuggestionAgg agg = suggestions.computeIfAbsent(key, k -> new SuggestionAgg());
                agg.qty = agg.qty.add(net);
                if (agg.suggestDate == null || suggestDate.isBefore(agg.suggestDate)) {
                    agg.suggestDate = suggestDate;
                }
                agg.level = Math.min(agg.level, node.level());
            }

            // 净需求>0 且为生产件时向下展开：子件毛需求 = 净需求 × 用量 × (1+损耗率)
            if (hasSuggestion && bom != null) {
                List<MrpBomItem> items = bomItemCache.getOrDefault(bom.getId(), List.of());
                if (!items.isEmpty() && node.level() + 1 >= MrpConstants.MAX_BOM_LEVEL) {
                    throw BusinessException.ofKey("erp.mrp.bom-explode-over-limit", MrpConstants.MAX_BOM_LEVEL, node.materialId());
                }
                Set<Long> childAncestors = new HashSet<>(node.ancestors());
                childAncestors.add(node.materialId());
                int idx = 0;
                for (MrpBomItem item : items) {
                    idx++;
                    if (childAncestors.contains(item.getMaterialId())) {
                        throw BusinessException.ofKey("erp.mrp.bom-circular", item.getMaterialId());
                    }
                    BigDecimal lossRate = item.getLossRate() == null ? BigDecimal.ZERO : item.getLossRate();
                    BigDecimal childGross = net.multiply(item.getQty())
                            .multiply(BigDecimal.ONE.add(lossRate.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                            .setScale(SCALE, RoundingMode.HALF_UP);
                    if (childGross.signum() <= 0) {
                        continue;
                    }
                    // 子件需求日期 = 父件建议开工日（父件开工前子件须到位）
                    queue.add(new Node(item.getMaterialId(), childGross, node.level() + 1,
                            node.path() + "/" + item.getMaterialId() + "#" + idx,
                            node.path(), suggestDate, childAncestors));
                }
            }
        }
    }

    /** 预扫展开树涉及的全部物料（visited 防环），同时缓存各级 BOM 子件 */
    private void preScan(MrpBom bom, Map<Long, List<MrpBomItem>> bomItemCache, Set<Long> allMaterialIds) {
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(bom.getMaterialId());
        Set<Long> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            Long materialId = queue.poll();
            if (!visited.add(materialId)) {
                continue;
            }
            MrpBom activeBom = bomService.findLatestActive(materialId);
            if (activeBom == null) {
                continue;
            }
            List<MrpBomItem> items = bomItemMapper.selectList(Wrappers.lambdaQuery(MrpBomItem.class)
                    .eq(MrpBomItem::getBomId, activeBom.getId()));
            bomItemCache.put(activeBom.getId(), items);
            for (MrpBomItem item : items) {
                allMaterialIds.add(item.getMaterialId());
                queue.add(item.getMaterialId());
            }
        }
    }

    private List<MrpMps> loadMps(MrpRunForm form) {
        if (form.getMpsIds() != null && !form.getMpsIds().isEmpty()) {
            List<MrpMps> list = mpsMapper.selectBatchIds(form.getMpsIds());
            if (list.size() != form.getMpsIds().size()) {
                throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.invalid-mps-line-id");
            }
            return list;
        }
        return mpsMapper.selectList(Wrappers.lambdaQuery(MrpMps.class)
                .eq(MrpMps::getStatus, MrpConstants.MPS_PENDING)
                .orderByAsc(MrpMps::getId));
    }

    private MrpBom requireActiveBom(Long materialId) {
        MrpBom bom = bomService.findLatestActive(materialId);
        if (bom == null) {
            MaterialBriefVO m = lookup.materialMap(List.of(materialId)).get(materialId);
            String name = m == null ? String.valueOf(materialId) : m.getCode() + "/" + m.getName();
            throw BusinessException.ofKey("erp.mrp.no-valid-bom-no-run", name);
        }
        return bom;
    }

    private boolean tableExists(String tableName) {
        return integrationMapper.tableExists(tableName) > 0;
    }

    private Map<Long, BigDecimal> sumMap(List<MaterialQtyVO> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream().collect(Collectors.toMap(
                MaterialQtyVO::getMaterialId,
                r -> r.getQty() == null ? BigDecimal.ZERO : r.getQty(),
                (a, b) -> a));
    }

    private CalcDetailTreeVO toTreeNode(MrpCalcDetail d, Map<Long, MaterialBriefVO> materials) {
        CalcDetailTreeVO node = new CalcDetailTreeVO();
        node.setNodePath(d.getNodePath());
        node.setLevel(d.getLevel());
        node.setMaterialId(d.getMaterialId());
        MaterialBriefVO m = materials.get(d.getMaterialId());
        if (m != null) {
            node.setCode(m.getCode());
            node.setName(m.getName());
            node.setUnit(m.getUnit());
        } else {
            node.setName("物料#" + d.getMaterialId());
        }
        node.setGrossQty(d.getGrossQty());
        node.setStockQty(d.getStockQty());
        node.setOnwayQty(d.getOnwayQty());
        node.setNetQty(d.getNetQty());
        node.setSuggestQty(d.getSuggestQty());
        node.setSuggestDate(d.getSuggestDate());
        node.setPlanType(d.getPlanType());
        return node;
    }
}
