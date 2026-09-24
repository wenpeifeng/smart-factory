package com.erp.report.service.impl;

import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.report.constant.DocTypes;
import com.erp.report.dto.vo.DocChainNodeVO;
import com.erp.report.dto.vo.DocChainVO;
import com.erp.report.mapper.BizReverseLogMapper;
import com.erp.report.mapper.DocChainMapper;
import com.erp.report.service.DocChainService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.report.entity.BizReverseLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 单据联查实现：邻接查询全部下推 DocChainMapper，本类只做 BFS 编排与节点补齐
 * （docType / docName / reversed 标记）。
 * <p>
 * 深度上限 {@link #MAX_DEPTH}，visited 以 (docType, docId) 去重防环（如 出库↔退货 双向边）。
 */
@Service
@RequiredArgsConstructor
public class DocChainServiceImpl implements DocChainService {

    /** BFS 深度上限（销售全链 报价→凭证 最多 6 跳，留余量） */
    private static final int MAX_DEPTH = 8;

    /** docType → 凭证侧 source_type（fin_voucher.source_type 的存量编码） */
    private static final Map<String, String> VOUCHER_SOURCE_TYPE = Map.of(
            DocTypes.RECEIPT, "RECEIPT",
            DocTypes.PAYMENT, "PAYMENT",
            DocTypes.PUR_INSTOCK, "PUR_INSTOCK",
            DocTypes.SAL_OUTSTOCK, "SAL_OUTSTOCK",
            DocTypes.PUR_RETURN, "PUR_RET",
            DocTypes.SAL_RETURN, "SAL_RET",
            DocTypes.SALES_INVOICE, "INVOICE_SALES",
            DocTypes.PURCHASE_INVOICE, "INVOICE_PURCHASE");

    private final DocChainMapper chainMapper;
    private final BizReverseLogMapper reverseLogMapper;

    @Override
    public DocChainVO chain(String bizType, Long bizId) {
        if (bizType == null || bizId == null || !DocTypes.LABELS.containsKey(bizType)) {
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.chain.unknown-biztype", bizType);
        }
        DocChainNodeVO anchor = anchorNode(bizType, bizId);
        if (anchor == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.biz.chain.doc-not-exists",
                    DocTypes.label(bizType), bizId);
        }
        List<DocChainNodeVO> upstream = bfs(anchor, true);
        List<DocChainNodeVO> downstream = bfs(anchor, false);
        return new DocChainVO(anchor, upstream, downstream);
    }

    // ==================== BFS 编排 ====================

    private List<DocChainNodeVO> bfs(DocChainNodeVO anchor, boolean up) {
        List<DocChainNodeVO> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        visited.add(anchor.getDocType() + ":" + anchor.getDocId());
        Deque<Level> queue = new ArrayDeque<>();
        queue.add(new Level(anchor, 1));
        while (!queue.isEmpty()) {
            Level cur = queue.poll();
            if (cur.depth >= MAX_DEPTH) {
                continue;
            }
            for (Neighbor n : neighbors(cur.node, up)) {
                String key = n.docType + ":" + n.docId;
                if (!visited.add(key)) {
                    continue;
                }
                DocChainNodeVO node = n.resolver.apply(n.docId);
                if (node == null) {
                    continue;
                }
                decorate(node, n.docType);
                result.add(node);
                queue.add(new Level(node, cur.depth + 1));
            }
        }
        return result;
    }

    /** 邻接描述：目标类型 + 取节点函数（按邻居 ID 查详情） */
    private record Neighbor(String docType, Long docId, Function<Long, DocChainNodeVO> resolver) {
    }

    private List<Neighbor> neighbors(DocChainNodeVO node, boolean up) {
        String t = node.getDocType();
        Long id = node.getDocId();
        List<Neighbor> ns = new ArrayList<>();
        // 红冲配对：原单 ↔ 红冲单（双向都可见）
        if (up) {
            addLogNeighbors(chainMapper.upSourceDocFromRed(t, id), ns, false);
        } else {
            addLogNeighbors(chainMapper.downRedDocFrom(t, id), ns, true);
        }
        if (up) {
            switch (t) {
                case DocTypes.SAL_ORDER -> addAll(ns, DocTypes.SAL_QUOTATION, chainMapper.upQuotationsFromOrder(id));
                case DocTypes.SAL_OUTSTOCK -> addAll(ns, DocTypes.SAL_ORDER, chainMapper.upOrderFromOutstock(id));
                case DocTypes.SAL_RETURN -> addAll(ns, DocTypes.SAL_OUTSTOCK, chainMapper.upOutstockFromSalReturn(id));
                case DocTypes.PUR_INSTOCK -> addAll(ns, DocTypes.PUR_ORDER, chainMapper.upOrderFromInstock(id));
                case DocTypes.PUR_RETURN -> addAll(ns, DocTypes.PUR_INSTOCK, chainMapper.upInstockFromPurReturn(id));
                case DocTypes.SALES_INVOICE -> addAll(ns, DocTypes.SAL_OUTSTOCK, chainMapper.upOutstockFromSalesInvoice(id));
                case DocTypes.PURCHASE_INVOICE -> addAll(ns, DocTypes.PUR_INSTOCK, chainMapper.upInstockFromPurchaseInvoice(id));
                case DocTypes.RECEIVABLE -> {
                    addAll(ns, DocTypes.SAL_OUTSTOCK, chainMapper.upOutstockFromReceivable(id));
                    addAll(ns, DocTypes.SAL_RETURN, chainMapper.upSalReturnFromReceivable(id));
                }
                case DocTypes.PAYABLE -> addAll(ns, DocTypes.PUR_INSTOCK, chainMapper.upInstockFromPayable(id));
                case DocTypes.RECEIPT -> addAll(ns, DocTypes.RECEIVABLE, chainMapper.upReceivablesFromReceipt(id));
                case DocTypes.PAYMENT -> addAll(ns, DocTypes.PAYABLE, chainMapper.upPayablesFromPayment(id));
                case DocTypes.VOUCHER -> resolveVoucherSources(id, ns);
                default -> { /* 报价/订单为源头 */ }
            }
        } else {
            switch (t) {
                case DocTypes.SAL_QUOTATION -> addAll(ns, DocTypes.SAL_ORDER, chainMapper.downOrderFromQuotation(id));
                case DocTypes.SAL_ORDER -> addAll(ns, DocTypes.SAL_OUTSTOCK, chainMapper.downOutstocksFromOrder(id));
                case DocTypes.SAL_OUTSTOCK -> {
                    addAll(ns, DocTypes.SAL_RETURN, chainMapper.downSalReturnsFromOutstock(id));
                    addAll(ns, DocTypes.SALES_INVOICE, chainMapper.downSalesInvoicesFromOutstock(id));
                    addAll(ns, DocTypes.RECEIVABLE, chainMapper.downReceivablesFromOutstock(id));
                    addVouchers(ns, DocTypes.SAL_OUTSTOCK, id);
                }
                case DocTypes.SAL_RETURN -> {
                    addAll(ns, DocTypes.RECEIVABLE, chainMapper.downReceivablesFromSalReturn(id));
                    addVouchers(ns, DocTypes.SAL_RETURN, id);
                }
                case DocTypes.PUR_ORDER -> addAll(ns, DocTypes.PUR_INSTOCK, chainMapper.downInstocksFromOrder(id));
                case DocTypes.PUR_INSTOCK -> {
                    addAll(ns, DocTypes.PUR_RETURN, chainMapper.downPurReturnsFromInstock(id));
                    addAll(ns, DocTypes.PURCHASE_INVOICE, chainMapper.downPurchaseInvoicesFromInstock(id));
                    addAll(ns, DocTypes.PAYABLE, chainMapper.downPayablesFromInstock(id));
                    addVouchers(ns, DocTypes.PUR_INSTOCK, id);
                }
                case DocTypes.PUR_RETURN -> addVouchers(ns, DocTypes.PUR_RETURN, id);
                case DocTypes.SALES_INVOICE -> addVouchers(ns, DocTypes.SALES_INVOICE, id);
                case DocTypes.PURCHASE_INVOICE -> addVouchers(ns, DocTypes.PURCHASE_INVOICE, id);
                case DocTypes.RECEIVABLE -> addAll(ns, DocTypes.RECEIPT, chainMapper.downReceiptsFromReceivable(id));
                case DocTypes.PAYABLE -> addAll(ns, DocTypes.PAYMENT, chainMapper.downPaymentsFromPayable(id));
                case DocTypes.RECEIPT -> addVouchers(ns, DocTypes.RECEIPT, id);
                case DocTypes.PAYMENT -> addVouchers(ns, DocTypes.PAYMENT, id);
                default -> { /* 凭证为链尾 */ }
            }
        }
        return ns;
    }

    /** 红冲配对邻居（节点已由 SQL 带出，直接解析 docType/docId 后再回查主表保证口径统一） */
    private void addLogNeighbors(List<DocChainNodeVO> logNodes, List<Neighbor> ns, boolean redSide) {
        for (DocChainNodeVO log : logNodes) {
            // log 行只是定位器（docId 即 biz_reverse_log.id）：
            // redSide=true 取配对的红冲单，否则取被红冲的原单
            BizReverseLog rl = reverseLogMapper.selectById(log.getDocId());
            if (rl == null) {
                continue;
            }
            if (redSide) {
                ns.add(new Neighbor(rl.getReverseType(), rl.getReverseId(),
                        x -> anchorNode(rl.getReverseType(), x)));
            } else {
                ns.add(new Neighbor(rl.getSourceType(), rl.getSourceId(),
                        x -> anchorNode(rl.getSourceType(), x)));
            }
        }
    }

    private void resolveVoucherSources(Long voucherId, List<Neighbor> ns) {
        for (DocChainNodeVO src : chainMapper.upSourcesFromVoucher(voucherId)) {
            String type = src.getDocType() == null ? DocTypes.RECEIPT : src.getDocType();
            addAll(ns, type, List.of(src));
        }
    }

    private void addVouchers(List<Neighbor> ns, String docType, Long id) {
        String sourceType = VOUCHER_SOURCE_TYPE.get(docType);
        if (sourceType != null) {
            addAll(ns, DocTypes.VOUCHER, chainMapper.downVouchersFromSource(sourceType, id));
        }
    }

    private void addAll(List<Neighbor> ns, String docType, List<DocChainNodeVO> nodes) {
        for (DocChainNodeVO n : nodes) {
            ns.add(new Neighbor(docType, n.getDocId(), x -> anchorNode(docType, x)));
        }
    }

    // ==================== 节点补齐 ====================

    /** 锚点/邻居节点详情（docType → 单表查询）；不存在返回 null */
    private DocChainNodeVO anchorNode(String docType, Long id) {
        DocChainNodeVO node = switch (docType) {
            case DocTypes.SAL_QUOTATION -> chainMapper.selectQuotation(id);
            case DocTypes.SAL_ORDER -> chainMapper.selectSalOrder(id);
            case DocTypes.SAL_OUTSTOCK -> chainMapper.selectOutstock(id);
            case DocTypes.SAL_RETURN -> chainMapper.selectSalReturn(id);
            case DocTypes.PUR_ORDER -> chainMapper.selectPurOrder(id);
            case DocTypes.PUR_INSTOCK -> chainMapper.selectInstock(id);
            case DocTypes.PUR_RETURN -> chainMapper.selectPurReturn(id);
            case DocTypes.SALES_INVOICE -> chainMapper.selectSalesInvoice(id);
            case DocTypes.PURCHASE_INVOICE -> chainMapper.selectPurchaseInvoice(id);
            case DocTypes.RECEIVABLE -> chainMapper.selectReceivable(id);
            case DocTypes.PAYABLE -> chainMapper.selectPayable(id);
            case DocTypes.RECEIPT -> chainMapper.selectReceipt(id);
            case DocTypes.PAYMENT -> chainMapper.selectPayment(id);
            case DocTypes.VOUCHER -> chainMapper.selectVoucher(id);
            default -> null;
        };
        if (node != null) {
            decorate(node, docType);
        }
        return node;
    }

    private void decorate(DocChainNodeVO node, String docType) {
        node.setDocType(docType);
        node.setDocName(DocTypes.label(docType));
        node.setReversed(reverseLogMapper.selectCount(Wrappers.lambdaQuery(BizReverseLog.class)
                .eq(BizReverseLog::getSourceType, docType)
                .eq(BizReverseLog::getSourceId, node.getDocId())) > 0);
    }

    private record Level(DocChainNodeVO node, int depth) {
    }
}
