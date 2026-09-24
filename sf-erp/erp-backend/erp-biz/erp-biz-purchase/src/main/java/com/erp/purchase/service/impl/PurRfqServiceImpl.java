package com.erp.purchase.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.framework.security.SecurityUtils;
import com.erp.purchase.dto.form.PurOrderForm;
import com.erp.purchase.dto.form.PurRfqForm;
import com.erp.purchase.dto.form.RfqAwardForm;
import com.erp.purchase.dto.form.RfqQuoteForm;
import com.erp.purchase.dto.query.PurRfqQuery;
import com.erp.purchase.dto.vo.PurRfqVO;
import com.erp.purchase.dto.vo.RfqComparisonVO;
import com.erp.purchase.dto.vo.RfqToOrderVO;
import com.erp.purchase.entity.PurRequisition;
import com.erp.purchase.entity.PurRequisitionItem;
import com.erp.purchase.entity.PurRfq;
import com.erp.purchase.entity.PurRfqItem;
import com.erp.purchase.entity.PurRfqQuote;
import com.erp.purchase.entity.PurRfqSupplier;
import com.erp.purchase.mapper.BasedataMapper;
import com.erp.purchase.mapper.PurRequisitionItemMapper;
import com.erp.purchase.mapper.PurRequisitionMapper;
import com.erp.purchase.mapper.PurRfqItemMapper;
import com.erp.purchase.mapper.PurRfqMapper;
import com.erp.purchase.mapper.PurRfqQuoteMapper;
import com.erp.purchase.mapper.PurRfqSupplierMapper;
import com.erp.purchase.service.OrderNoGenerator;
import com.erp.purchase.service.PurOrderService;
import com.erp.purchase.service.PurRfqService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 询比价单 Service 实现。
 * <p>
 * 关键约束：
 * <ul>
 *   <li>已定标禁改：修改/录报价/删除仅限 0询价中（定标结果与报价留痕不可篡改）；</li>
 *   <li>超转守卫双层：RFQ 创建时应用层校验 询价数量 &lt;= 请购数量-已转数量，
 *       定标时数据库条件 UPDATE（ordered_qty + delta &lt;= qty）兜底并发竞态；</li>
 *   <li>下推仅一次：RFQ 明细回写 pur_order_id 后禁止再次下推；
 *       下推组装订单草稿走既有 {@link PurOrderService#create}（同一事务，一供应商一单）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PurRfqServiceImpl implements PurRfqService {

    /** 询比价最少邀请供应商数（三家起报，比价才有意义） */
    private static final int MIN_SUPPLIERS = 3;

    private final PurRfqMapper rfqMapper;
    private final PurRfqSupplierMapper supplierMapper;
    private final PurRfqItemMapper itemMapper;
    private final PurRfqQuoteMapper quoteMapper;
    private final PurRequisitionMapper requisitionMapper;
    private final PurRequisitionItemMapper requisitionItemMapper;
    private final BasedataMapper basedataMapper;
    private final OrderNoGenerator orderNoGenerator;
    /** 下推组装采购订单草稿复用既有订单服务（金额/单号/校验口径与手工建单完全一致） */
    private final PurOrderService purOrderService;

    @Override
    public PageResult<PurRfqVO> page(PurRfqQuery query) {
        var page = rfqMapper.selectRfqPage(new Page<>(query.getPageNum(), query.getPageSize()), query);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PurRfqVO detail(Long id) {
        PurRfq rfq = requireRfq(id);
        PurRfqVO vo = toVo(rfq);
        vo.setSuppliers(supplierMapper.selectSuppliersWithNames(id));
        vo.setItems(itemMapper.selectItemsWithMaterial(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PurRfqForm form) {
        validateSuppliers(form.getSupplierIds());
        form.getItems().forEach(line -> {
            if (basedataMapper.countMaterialById(line.getMaterialId()) == 0) {
                throw BusinessException.ofKey("erp.pur.material-not-exists", line.getMaterialId());
            }
        });
        // 关联请购时逐行做超转校验（应用层预检；定标时数据库条件 UPDATE 再兜底）
        if (form.getRequisitionId() != null) {
            checkRequisitionRemaining(form.getRequisitionId(), form.getItems());
        }
        PurRfq rfq = new PurRfq();
        rfq.setRfqNo(orderNoGenerator.next("IN"));
        rfq.setRequisitionId(form.getRequisitionId());
        rfq.setDeadline(form.getDeadline());
        rfq.setStatus(PurRfq.STATUS_QUOTING);
        rfq.setRemark(form.getRemark());
        rfqMapper.insert(rfq);
        form.getSupplierIds().forEach(sid -> {
            PurRfqSupplier s = new PurRfqSupplier();
            s.setRfqId(rfq.getId());
            s.setSupplierId(sid);
            s.setQuoteAmount(BigDecimal.ZERO);
            s.setStatus(PurRfqSupplier.STATUS_INVITED);
            supplierMapper.insert(s);
        });
        form.getItems().forEach(line -> {
            PurRfqItem item = new PurRfqItem();
            item.setRfqId(rfq.getId());
            item.setRequisitionItemId(line.getRequisitionItemId());
            item.setMaterialId(line.getMaterialId());
            item.setQty(line.getQty());
            item.setRemark(line.getRemark());
            itemMapper.insert(item);
        });
        return rfq.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PurRfqForm form) {
        PurRfq rfq = requireRfq(id);
        if (rfq.getStatus() != PurRfq.STATUS_QUOTING) {
            throw BusinessException.ofKey("erp.pur.rfq-award-cancelled-no-modify", rfq.getStatus());
        }
        if (form.getRequisitionId() != null && !Objects.equals(form.getRequisitionId(), rfq.getRequisitionId())) {
            throw BusinessException.ofKey("erp.pur.source-pr-immutable");
        }
        validateSuppliers(form.getSupplierIds());
        // 询价明细不可改（防与已录报价错位）：仅调整截止日期/备注/邀请名单
        rfq.setDeadline(form.getDeadline());
        rfq.setRemark(form.getRemark());
        rfqMapper.updateById(rfq);
        rewriteSuppliers(id, form.getSupplierIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveQuotes(Long id, RfqQuoteForm form) {
        PurRfq rfq = requireRfq(id);
        if (rfq.getStatus() != PurRfq.STATUS_QUOTING) {
            throw BusinessException.ofKey("erp.pur.rfq-awarded-no-quote", rfq.getStatus());
        }
        PurRfqSupplier supplierRow = supplierMapper.selectOne(Wrappers.lambdaQuery(PurRfqSupplier.class)
                .eq(PurRfqSupplier::getRfqId, id)
                .eq(PurRfqSupplier::getSupplierId, form.getSupplierId()));
        if (supplierRow == null) {
            throw BusinessException.ofKey("erp.pur.supplier-not-invited", form.getSupplierId());
        }
        List<PurRfqItem> items = itemMapper.selectList(Wrappers.lambdaQuery(PurRfqItem.class)
                .eq(PurRfqItem::getRfqId, id));
        Map<Long, PurRfqItem> itemMap = items.stream()
                .collect(Collectors.toMap(PurRfqItem::getId, Function.identity()));
        for (RfqQuoteForm.QuoteLineForm line : form.getQuotes()) {
            if (!itemMap.containsKey(line.getItemId())) {
                throw BusinessException.ofKey("erp.pur.quote-line-not-in-rfq", line.getItemId());
            }
        }
        // 同供应商重录 = 覆盖：物理删旧报价行再插入（uk_quote 真实唯一索引，
        // 逻辑删后旧行仍占索引会 Duplicate key，见 PurRfqQuoteMapper 类注释）
        quoteMapper.physicalDeleteBySupplier(id, form.getSupplierId());
        List<BigDecimal> lineTotals = new ArrayList<>();
        form.getQuotes().forEach(line -> {
            PurRfqItem item = itemMap.get(line.getItemId());
            PurRfqQuote quote = new PurRfqQuote();
            quote.setRfqId(id);
            quote.setSupplierId(form.getSupplierId());
            quote.setRfqItemId(line.getItemId());
            quote.setMaterialId(item.getMaterialId());
            quote.setPrice(line.getPrice());
            quote.setLeadDays(line.getLeadDays());
            quoteMapper.insert(quote);
            lineTotals.add(DocCalc.lineAmount(item.getQty(), line.getPrice()));
        });
        supplierRow.setQuoteAmount(DocCalc.total(lineTotals));
        supplierRow.setStatus(PurRfqSupplier.STATUS_QUOTED);
        supplierMapper.updateById(supplierRow);
    }

    @Override
    public RfqComparisonVO comparison(Long id) {
        PurRfq rfq = requireRfq(id);
        List<PurRfqSupplier> suppliers = supplierMapper.selectList(Wrappers.lambdaQuery(PurRfqSupplier.class)
                .eq(PurRfqSupplier::getRfqId, id));
        // 复用明细关联查询（JOIN base_material，含历史停用资料），矩阵行直接带物料信息
        List<PurRfqVO.ItemVO> items = itemMapper.selectItemsWithMaterial(id);
        List<PurRfqQuote> quotes = quoteMapper.selectList(Wrappers.lambdaQuery(PurRfqQuote.class)
                .eq(PurRfqQuote::getRfqId, id));

        RfqComparisonVO vo = new RfqComparisonVO();
        vo.setRfqId(id);
        vo.setRfqNo(rfq.getRfqNo());
        vo.setStatus(rfq.getStatus());
        vo.setDeadline(rfq.getDeadline() == null ? null : rfq.getDeadline().toString());
        vo.setSuppliers(suppliers.stream().map(s -> {
            RfqComparisonVO.SupplierHead head = new RfqComparisonVO.SupplierHead();
            head.setSupplierId(s.getSupplierId());
            head.setSupplierName(basedataMapper.selectSupplierName(s.getSupplierId()));
            head.setStatus(s.getStatus());
            head.setQuoteAmount(s.getQuoteAmount());
            return head;
        }).toList());

        Map<Long, List<PurRfqQuote>> quotesByItem = quotes.stream()
                .collect(Collectors.groupingBy(PurRfqQuote::getRfqItemId));
        vo.setItems(items.stream().map(item -> {
            RfqComparisonVO.ItemRow row = new RfqComparisonVO.ItemRow();
            row.setItemId(item.getId());
            row.setMaterialCode(item.getMaterialCode());
            row.setMaterialName(item.getMaterialName());
            row.setSpec(item.getSpec());
            row.setUnit(item.getUnit());
            row.setQty(item.getQty());
            row.setAwardSupplierId(item.getAwardSupplierId());
            List<RfqComparisonVO.QuoteCell> cells = quotesByItem
                    .getOrDefault(item.getId(), List.of()).stream()
                    .map(q -> {
                        RfqComparisonVO.QuoteCell cell = new RfqComparisonVO.QuoteCell();
                        cell.setSupplierId(q.getSupplierId());
                        cell.setPrice(q.getPrice());
                        cell.setLeadDays(q.getLeadDays());
                        cell.setTotal(DocCalc.lineAmount(item.getQty(), q.getPrice()));
                        return cell;
                    }).toList();
            // 每物料标记最低价（并列最低都标记，辅助分项定标决策）
            cells.stream().map(RfqComparisonVO.QuoteCell::getPrice).min(BigDecimal::compareTo)
                    .ifPresent(min -> cells.forEach(c -> c.setLowest(c.getPrice().compareTo(min) == 0)));
            row.setQuotes(cells);
            return row;
        }).toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void award(Long id, RfqAwardForm form) {
        PurRfq rfq = requireRfq(id);
        if (rfq.getStatus() != PurRfq.STATUS_QUOTING) {
            throw BusinessException.ofKey("erp.pur.rfq-only-quoting-award", rfq.getStatus());
        }
        List<PurRfqItem> items = itemMapper.selectList(Wrappers.lambdaQuery(PurRfqItem.class)
                .eq(PurRfqItem::getRfqId, id));
        // perItem 必须与明细一一对应（不重不漏）
        Map<Long, Long> awardMap = new LinkedHashMap<>();
        for (RfqAwardForm.AwardItemForm line : form.getPerItem()) {
            if (awardMap.put(line.getItemId(), line.getSupplierId()) != null) {
                throw BusinessException.ofKey("erp.pur.multiple-award-lines", line.getItemId());
            }
        }
        if (!awardMap.keySet().equals(items.stream().map(PurRfqItem::getId).collect(Collectors.toSet()))) {
            throw BusinessException.ofKey("erp.pur.award-must-cover-all");
        }
        // 中标供应商必须已对该明细报价（中标单价/交期取自报价行）
        List<PurRfqQuote> quotes = quoteMapper.selectList(Wrappers.lambdaQuery(PurRfqQuote.class)
                .eq(PurRfqQuote::getRfqId, id));
        Map<Long, Map<Long, PurRfqQuote>> quotesByItem = quotes.stream().collect(
                Collectors.groupingBy(PurRfqQuote::getRfqItemId,
                        Collectors.toMap(PurRfqQuote::getSupplierId, Function.identity())));
        Map<Long, PurRfqItem> itemMap = items.stream()
                .collect(Collectors.toMap(PurRfqItem::getId, Function.identity()));
        for (Map.Entry<Long, Long> e : awardMap.entrySet()) {
            PurRfqQuote quote = quotesByItem
                    .getOrDefault(e.getKey(), Map.of()).get(e.getValue());
            if (quote == null) {
                throw BusinessException.ofKey("erp.pur.supplier-no-quote-no-award", e.getValue(), e.getKey());
            }
        }
        // 应用层超转预检：来源请购单须未关闭（关闭单不允许再定标回写）；
        // 同一请购明细被多个 RFQ 行引用时按累计口径校验
        if (rfq.getRequisitionId() != null) {
            PurRequisition req = requisitionMapper.selectById(rfq.getRequisitionId());
            if (req == null || req.getStatus() == PurRequisition.STATUS_CLOSED) {
                throw BusinessException.ofKey("erp.pur.source-pr-closed-no-award", rfq.getRequisitionId());
            }
        }
        Map<Long, BigDecimal> deltaByReqItem = new LinkedHashMap<>();
        items.forEach(item -> {
            if (item.getRequisitionItemId() != null) {
                deltaByReqItem.merge(item.getRequisitionItemId(), item.getQty(), BigDecimal::add);
            }
        });
        deltaByReqItem.forEach((reqItemId, delta) -> {
            PurRequisitionItem reqItem = requisitionItemMapper.selectById(reqItemId);
            if (reqItem == null) {
                throw BusinessException.ofKey("erp.pur.source-pr-line-not-exists", reqItemId);
            }
            if (reqItem.getOrderedQty().add(delta).compareTo(reqItem.getQty()) > 0) {
                throw BusinessException.ofKey("erp.pur.award-over-pr-qty", reqItemId, reqItem.getQty().subtract(reqItem.getOrderedQty()), delta);
            }
        });
        // 条件 UPDATE 占状态：并发重复定标第二次影响 0 行直接失败
        int rows = rfqMapper.award(id, SecurityUtils.getCurrentUserId());
        if (rows == 0) {
            throw BusinessException.ofKey("erp.pur.rfq-only-quoting-award2");
        }
        // 回写明细定标信息 + 标记中标供应商行
        Map<Long, PurRfqSupplier> supplierRows = supplierMapper
                .selectList(Wrappers.lambdaQuery(PurRfqSupplier.class)
                        .eq(PurRfqSupplier::getRfqId, id))
                .stream().collect(Collectors.toMap(PurRfqSupplier::getSupplierId, Function.identity()));
        awardMap.forEach((itemId, supplierId) -> {
            PurRfqQuote quote = quotesByItem.get(itemId).get(supplierId);
            PurRfqItem item = itemMap.get(itemId);
            PurRfqItem upd = new PurRfqItem();
            upd.setId(itemId);
            upd.setAwardSupplierId(supplierId);
            upd.setAwardPrice(quote.getPrice());
            upd.setAwardLeadDays(quote.getLeadDays());
            itemMapper.updateById(upd);
            PurRfqSupplier supplierRow = supplierRows.get(supplierId);
            if (supplierRow != null) {
                supplierMapper.markWon(supplierRow.getId());
            }
            // 请购已转数量回写：数据库层兜底（影响 0 行=超转，整体回滚）
            if (item.getRequisitionItemId() != null) {
                int updated = requisitionItemMapper
                        .addOrderedQtyGuarded(item.getRequisitionItemId(), item.getQty());
                if (updated == 0) {
                    throw BusinessException.ofKey("erp.pur.award-over-pr-qty-concurrent", item.getRequisitionItemId());
                }
            }
        });
        // 请购单状态推进为已转采购（10 -> 20；已是 20 幂等保持）
        if (rfq.getRequisitionId() != null) {
            requisitionMapper.markOrdered(rfq.getRequisitionId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RfqToOrderVO toOrders(Long id) {
        PurRfq rfq = requireRfq(id);
        if (rfq.getStatus() != PurRfq.STATUS_AWARDED) {
            throw BusinessException.ofKey("erp.pur.rfq-only-awarded-push", rfq.getStatus());
        }
        List<PurRfqItem> items = itemMapper.selectList(Wrappers.lambdaQuery(PurRfqItem.class)
                .eq(PurRfqItem::getRfqId, id));
        if (items.stream().anyMatch(i -> i.getPurOrderId() != null)) {
            throw BusinessException.ofKey("erp.pur.rfq-already-pushed", rfq.getRfqNo());
        }
        // 按定标供应商分组：一供应商一单（分组保持明细出现顺序，订单内明细按 id 升序）
        Map<Long, List<PurRfqItem>> bySupplier = items.stream()
                .collect(Collectors.groupingBy(PurRfqItem::getAwardSupplierId,
                        LinkedHashMap::new, Collectors.toList()));
        RfqToOrderVO vo = new RfqToOrderVO();
        vo.setRfqNo(rfq.getRfqNo());
        List<RfqToOrderVO.CreatedOrder> created = new ArrayList<>();
        LocalDate today = LocalDate.now();
        bySupplier.forEach((supplierId, group) -> {
            PurOrderForm form = new PurOrderForm();
            form.setSupplierId(supplierId);
            form.setOrderDate(today);
            form.setRemark("询比价 " + rfq.getRfqNo() + " 生成");
            form.setDetails(group.stream().map(item -> {
                PurOrderForm.PurOrderDetailForm line = new PurOrderForm.PurOrderDetailForm();
                line.setMaterialId(item.getMaterialId());
                line.setQty(item.getQty());
                line.setPrice(item.getAwardPrice());
                // 交货日期 = 下推日 + 中标交期；税率报价未采集，留空按 0 存储（不影响含税口径）
                line.setDeliveryDate(today.plusDays(item.getAwardLeadDays() == null
                        ? 0 : item.getAwardLeadDays()));
                line.setRemark(item.getRemark());
                return line;
            }).toList());
            // 复用既有订单创建：单号/金额服务端计算、供应商物料校验与手工建单一致
            Long orderId = purOrderService.create(form);
            group.forEach(item -> itemMapper.bindOrderId(item.getId(), orderId));
            RfqToOrderVO.CreatedOrder co = new RfqToOrderVO.CreatedOrder();
            co.setSupplierId(supplierId);
            co.setSupplierName(basedataMapper.selectSupplierName(supplierId));
            co.setOrderId(orderId);
            co.setOrderNo(purOrderService.detail(orderId).getOrderNo());
            created.add(co);
        });
        vo.setOrders(created);
        return vo;
    }

    @Override
    public void cancel(Long id) {
        int rows = rfqMapper.cancel(id);
        if (rows == 0) {
            requireRfq(id);
            throw BusinessException.ofKey("erp.pur.rfq-only-quoting-cancel");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PurRfq rfq = requireRfq(id);
        if (rfq.getStatus() != PurRfq.STATUS_QUOTING) {
            throw BusinessException.ofKey("erp.pur.rfq-only-quoting-delete", rfq.getStatus());
        }
        rfqMapper.deleteById(id);
        supplierMapper.delete(Wrappers.lambdaQuery(PurRfqSupplier.class)
                .eq(PurRfqSupplier::getRfqId, id));
        itemMapper.delete(Wrappers.lambdaQuery(PurRfqItem.class).eq(PurRfqItem::getRfqId, id));
        quoteMapper.delete(Wrappers.lambdaQuery(PurRfqQuote.class).eq(PurRfqQuote::getRfqId, id));
    }

    /** 加载询比价单，不存在（含已逻辑删）抛业务异常 */
    private PurRfq requireRfq(Long id) {
        PurRfq rfq = rfqMapper.selectById(id);
        if (rfq == null) {
            throw BusinessException.ofKey("erp.pur.rfq-not-exists", id);
        }
        return rfq;
    }

    /** 校验邀请供应商：去重后至少 MIN_SUPPLIERS 家且全部在用 */
    private void validateSuppliers(List<Long> supplierIds) {
        Set<Long> distinct = new HashSet<>(supplierIds);
        if (distinct.size() != supplierIds.size()) {
            throw BusinessException.ofKey("erp.pur.duplicate-invited-suppliers");
        }
        if (distinct.size() < MIN_SUPPLIERS) {
            throw BusinessException.ofKey("erp.pur.rfq-min-suppliers", MIN_SUPPLIERS, distinct.size());
        }
        distinct.forEach(sid -> {
            if (basedataMapper.countSupplierById(sid) == 0) {
                throw BusinessException.ofKey("erp.pur.supplier-not-exists", sid);
            }
        });
    }

    /**
     * 关联请购的可转余量校验：请购单须为已提交/已转采购状态，
     * 每行 询价数量 &lt;= 请购数量 - 已转采购数量（多个 RFQ 行指向同一请购明细时按累计口径）。
     */
    private void checkRequisitionRemaining(Long requisitionId, List<PurRfqForm.ItemForm> items) {
        PurRequisition req = requisitionMapper.selectById(requisitionId);
        if (req == null) {
            throw BusinessException.ofKey("erp.pur.source-pr-not-exists", requisitionId);
        }
        if (req.getStatus() != PurRequisition.STATUS_SUBMITTED
                && req.getStatus() != PurRequisition.STATUS_ORDERED) {
            throw BusinessException.ofKey("erp.pur.pr-status-no-to-rfq", req.getReqNo());
        }
        Map<Long, BigDecimal> wanted = new LinkedHashMap<>();
        items.forEach(line -> {
            if (line.getRequisitionItemId() != null) {
                wanted.merge(line.getRequisitionItemId(), line.getQty(), BigDecimal::add);
            }
        });
        wanted.forEach((reqItemId, qty) -> {
            PurRequisitionItem reqItem = requisitionItemMapper.selectById(reqItemId);
            if (reqItem == null || !Objects.equals(reqItem.getRequisitionId(), requisitionId)) {
                throw BusinessException.ofKey("erp.pur.pr-line-not-in-source", reqItemId);
            }
            BigDecimal available = reqItem.getQty().subtract(reqItem.getOrderedQty());
            if (qty.compareTo(available) > 0) {
                throw BusinessException.ofKey("erp.pur.quote-over-pr-remaining", reqItemId, available, qty);
            }
        });
    }

    /** 重写邀请名单：移出的供应商删行并清报价，保留行原状态不动（已报价不受影响） */
    private void rewriteSuppliers(Long rfqId, List<Long> supplierIds) {
        List<PurRfqSupplier> existing = supplierMapper.selectList(
                Wrappers.lambdaQuery(PurRfqSupplier.class).eq(PurRfqSupplier::getRfqId, rfqId));
        Set<Long> invited = new HashSet<>(supplierIds);
        existing.forEach(row -> {
            if (!invited.contains(row.getSupplierId())) {
                // 物理删：uk_rfq_supplier 唯一索引占用问题（见 PurRfqSupplierMapper 类注释），
                // 报价行随邀请行一并清理
                supplierMapper.physicalDeleteBySupplier(rfqId, row.getSupplierId());
                quoteMapper.physicalDeleteBySupplier(rfqId, row.getSupplierId());
            }
        });
        Set<Long> existingIds = existing.stream()
                .map(PurRfqSupplier::getSupplierId).collect(Collectors.toSet());
        invited.stream().filter(sid -> !existingIds.contains(sid)).forEach(sid -> {
            PurRfqSupplier s = new PurRfqSupplier();
            s.setRfqId(rfqId);
            s.setSupplierId(sid);
            s.setQuoteAmount(BigDecimal.ZERO);
            s.setStatus(PurRfqSupplier.STATUS_INVITED);
            supplierMapper.insert(s);
        });
    }

    /** 实体 -> VO（列表/详情通用） */
    private PurRfqVO toVo(PurRfq rfq) {
        PurRfqVO vo = new PurRfqVO();
        vo.setId(rfq.getId());
        vo.setRfqNo(rfq.getRfqNo());
        vo.setRequisitionId(rfq.getRequisitionId());
        if (rfq.getRequisitionId() != null) {
            PurRequisition req = requisitionMapper.selectById(rfq.getRequisitionId());
            vo.setReqNo(req == null ? null : req.getReqNo());
        }
        vo.setDeadline(rfq.getDeadline());
        vo.setStatus(rfq.getStatus());
        vo.setAwardTime(rfq.getAwardTime());
        vo.setRemark(rfq.getRemark());
        vo.setCreateTime(rfq.getCreateTime());
        return vo;
    }
}
