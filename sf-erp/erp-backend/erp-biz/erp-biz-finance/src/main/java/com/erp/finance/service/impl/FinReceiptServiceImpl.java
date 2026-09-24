package com.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.finance.dto.form.ReceiptForm;
import com.erp.finance.dto.request.ReceiptQuery;
import com.erp.finance.dto.vo.ReceiptDetailVO;
import com.erp.finance.dto.vo.ReceiptItemVO;
import com.erp.finance.dto.vo.ReceiptVO;
import com.erp.finance.context.BookContext;
import com.erp.finance.entity.FinAccount;
import com.erp.finance.entity.FinAccountFlow;
import com.erp.finance.entity.FinReceipt;
import com.erp.finance.entity.FinReceiptItem;
import com.erp.finance.entity.FinReceivable;
import com.erp.finance.mapper.FinAccountFlowMapper;
import com.erp.finance.mapper.FinAccountMapper;
import com.erp.finance.mapper.FinReceiptItemMapper;
import com.erp.finance.mapper.FinReceiptMapper;
import com.erp.finance.mapper.FinReceivableMapper;
import com.erp.finance.service.FinReceiptService;
import com.erp.finance.support.FinanceBizNoGenerator;
import com.erp.finance.support.PartnerNameLookup;
import com.erp.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收款单服务实现 —— 收款核销应收的核心事务。
 * <p>
 * 事务边界（create 全程 @Transactional，任一步失败整体回滚）：
 * <ol>
 *   <li>校验：收款金额 == 明细核销额之和；明细应收同属一个客户；</li>
 *   <li>逐笔条件 UPDATE 回写应收（settled_amount 累加 + 状态联动，
 *       WHERE 兜底"核销额 ≤ 未核销余额"，并发安全）；</li>
 *   <li>资金账户余额原子增加（条件 UPDATE）；</li>
 *   <li>写入收款单主子表；</li>
 *   <li>写入账户流水（含记账后余额快照）。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class FinReceiptServiceImpl implements FinReceiptService {

    private final FinReceiptMapper receiptMapper;
    private final FinReceiptItemMapper receiptItemMapper;
    private final FinReceivableMapper receivableMapper;
    private final FinAccountMapper accountMapper;
    private final FinAccountFlowMapper flowMapper;
    private final PartnerNameLookup partnerNameLookup;
    private final FinanceBizNoGenerator bizNoGenerator;

    @Override
    public PageResult<ReceiptVO> page(ReceiptQuery query, long pageNum, long pageSize) {
        // MP 的 like(condition, column, val) 中 val 是即时求值：keyword 为 null 时不能在此处调 trim()
        String kw = query.keyword() == null ? "" : query.keyword().trim();
        var wrapper = Wrappers.lambdaQuery(FinReceipt.class)
                .eq(FinReceipt::getBookId, BookContext.getBookId())
                .eq(query.customerId() != null, FinReceipt::getCustomerId, query.customerId())
                .like(!kw.isBlank(), FinReceipt::getReceiptNo, kw)
                .orderByDesc(FinReceipt::getId);
        PageResult<FinReceipt> page = PageUtils.of(receiptMapper, wrapper, pageNum, pageSize);
        // 名称回填：客户 + 账户一次批量查询
        Map<Long, String> customerNames = partnerNameLookup.customerNames(
                page.getRecords().stream().map(FinReceipt::getCustomerId).toList());
        Map<Long, String> accountNames = accountNames(page.getRecords().stream()
                .map(FinReceipt::getAccountId).toList());
        return page.map(r -> ReceiptVO.from(r,
                customerNames.get(r.getCustomerId()), accountNames.get(r.getAccountId())));
    }

    @Override
    public ReceiptDetailVO detail(Long id) {
        FinReceipt receipt = requireReceipt(id);
        List<FinReceiptItem> items = receiptItemMapper.selectList(
                Wrappers.lambdaQuery(FinReceiptItem.class)
                        .eq(FinReceiptItem::getReceiptId, id)
                        .orderByAsc(FinReceiptItem::getId));
        List<Long> receivableIds = items.stream().map(FinReceiptItem::getReceivableId).toList();
        Map<Long, FinReceivable> receivables = receivableIds.isEmpty() ? Map.of()
                : receivableMapper.selectBatchIds(receivableIds).stream()
                        .collect(Collectors.toMap(FinReceivable::getId, Function.identity()));

        List<ReceiptItemVO> itemVOs = items.stream().map(item -> {
            FinReceivable r = receivables.get(item.getReceivableId());
            return new ReceiptItemVO(item.getReceivableId(),
                    r == null ? null : r.getBizNo(),
                    r == null ? null : r.getAmount(),
                    r == null ? null : r.getSettledAmount(),
                    item.getVerifyAmount());
        }).toList();

        FinAccount account = accountMapper.selectById(receipt.getAccountId());
        String customerName = partnerNameLookup
                .customerNames(List.of(receipt.getCustomerId())).get(receipt.getCustomerId());
        return new ReceiptDetailVO(receipt.getId(), receipt.getReceiptNo(), receipt.getCustomerId(),
                customerName, receipt.getAccountId(), account == null ? null : account.getName(),
                receipt.getBizDate(), receipt.getAmount(), receipt.getVerifiedAmount(),
                receipt.getRemark(), receipt.getCreateTime(), itemVOs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ReceiptForm form) {
        BigDecimal amount = form.amount();
        Long bookId = BookContext.getBookId();
        // 1. 基础校验：金额一致性与账户可用性（账户必须属于当前账套）
        BigDecimal itemSum = BigDecimal.ZERO;
        for (ReceiptForm.Item item : form.items()) {
            itemSum = itemSum.add(item.verifyAmount());
        }
        if (itemSum.compareTo(amount) != 0) {
            throw BusinessException.ofKey("erp.fin.receipt-amount-mismatch", amount.toPlainString(), itemSum.toPlainString());
        }
        FinAccount account = accountMapper.selectById(form.accountId());
        if (account == null || account.getStatus() == null || account.getStatus() != 1) {
            throw BusinessException.ofKey("erp.fin.receipt-account-not-exists");
        }
        BookContext.checkBookOwnership(account.getBookId(),
                "收款账户[%s %s]".formatted(account.getAccountNo(), account.getName()));

        // 2. 逐笔核销应收（先做归属校验，再条件 UPDATE；失败抛异常整体回滚）
        for (ReceiptForm.Item item : form.items()) {
            FinReceivable receivable = receivableMapper.selectById(item.receivableId());
            if (receivable == null) {
                throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.receivable-not-exists", item.receivableId());
            }
            BookContext.checkBookOwnership(receivable.getBookId(),
                    "应收单[%s]".formatted(receivable.getBizNo()));
            if (!receivable.getCustomerId().equals(form.customerId())) {
                throw BusinessException.ofKey("erp.fin.receivable-not-same-customer", receivable.getBizNo());
            }
            int rows = receivableMapper.applyVerify(bookId, item.receivableId(), item.verifyAmount());
            if (rows == 0) {
                throw BusinessException.ofKey("erp.fin.receivable-writeoff-over", receivable.getBizNo(), item.verifyAmount().toPlainString(), receivable.getAmount().subtract(receivable.getSettledAmount()).toPlainString());
            }
        }

        // 3. 账户余额原子增加
        int rows = accountMapper.changeBalance(bookId, form.accountId(), amount, amount);
        if (rows == 0) {
            throw BusinessException.ofKey("erp.fin.receipt-balance-update-failed");
        }

        // 4. 收款单主子表落库
        FinReceipt receipt = new FinReceipt();
        receipt.setBookId(bookId);
        receipt.setReceiptNo(bizNoGenerator.next(FinanceBizNoGenerator.RECEIPT));
        receipt.setCustomerId(form.customerId());
        receipt.setAccountId(form.accountId());
        receipt.setBizDate(form.bizDate());
        receipt.setAmount(amount);
        receipt.setVerifiedAmount(amount);
        receipt.setRemark(form.remark());
        receiptMapper.insert(receipt);
        for (ReceiptForm.Item item : form.items()) {
            FinReceiptItem receiptItem = new FinReceiptItem();
            receiptItem.setBookId(bookId);
            receiptItem.setReceiptId(receipt.getId());
            receiptItem.setReceivableId(item.receivableId());
            receiptItem.setVerifyAmount(item.verifyAmount());
            receiptItemMapper.insert(receiptItem);
        }

        // 5. 账户流水（重查拿记账后余额快照）
        FinAccount after = accountMapper.selectById(form.accountId());
        FinAccountFlow flow = new FinAccountFlow();
        flow.setBookId(bookId);
        flow.setAccountId(form.accountId());
        flow.setBizDate(form.bizDate());
        flow.setBizType("RECEIPT");
        flow.setBizId(receipt.getId());
        flow.setBizNo(receipt.getReceiptNo());
        flow.setDirection("INCOME");
        flow.setAmount(amount);
        flow.setBalanceAfter(after.getCurrentBalance());
        flow.setRemark("收款核销：" + receipt.getReceiptNo());
        flowMapper.insert(flow);
        return receipt.getId();
    }

    /** 收款单必须存在且属于当前账套 */
    private FinReceipt requireReceipt(Long id) {
        FinReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.receipt-not-exists", id);
        }
        BookContext.checkBookOwnership(receipt.getBookId(), "收款单[%s]".formatted(receipt.getReceiptNo()));
        return receipt;
    }

    /** 账户 ID 集合 → id -> 名称 映射（本模块自有表，直接查） */
    private Map<Long, String> accountNames(List<Long> accountIds) {
        return accountIds.isEmpty() ? Map.of()
                : accountMapper.selectBatchIds(accountIds).stream()
                        .collect(Collectors.toMap(FinAccount::getId, FinAccount::getName));
    }
}
