package com.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.finance.dto.form.AccountForm;
import com.erp.finance.dto.vo.AccountBalanceVO;
import com.erp.finance.dto.vo.AccountFlowVO;
import com.erp.finance.dto.vo.AccountVO;
import com.erp.finance.context.BookContext;
import com.erp.finance.entity.FinAccount;
import com.erp.finance.entity.FinAccountFlow;
import com.erp.finance.mapper.FinAccountFlowMapper;
import com.erp.finance.mapper.FinAccountMapper;
import com.erp.finance.service.FinAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 资金账户服务实现。
 * <p>
 * 期初余额调整通过 {@link FinAccountMapper#changeBalance} 条件 UPDATE 差额同步，
 * 保证不会把余额调成负数。
 */
@Service
@RequiredArgsConstructor
public class FinAccountServiceImpl implements FinAccountService {

    private final FinAccountMapper accountMapper;
    private final FinAccountFlowMapper flowMapper;

    @Override
    public List<AccountVO> list() {
        return accountMapper.selectList(Wrappers.lambdaQuery(FinAccount.class)
                        .eq(FinAccount::getBookId, BookContext.getBookId())
                        .orderByAsc(FinAccount::getId))
                .stream().map(AccountVO::from).toList();
    }

    @Override
    public void create(AccountForm form) {
        checkAccountNoUnique(form.accountNo(), null);
        FinAccount account = new FinAccount();
        applyForm(account, form);
        account.setBookId(BookContext.getBookId());
        account.setCurrentBalance(form.initialBalance());
        accountMapper.insert(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AccountForm form) {
        FinAccount account = requireAccount(id);
        checkAccountNoUnique(form.accountNo(), id);
        BigDecimal delta = form.initialBalance().subtract(account.getInitialBalance());
        applyForm(account, form);
        accountMapper.updateById(account);
        if (delta.signum() != 0) {
            // 期初余额变动 → 当前余额同步差额调整；调整为负时条件 UPDATE 影响 0 行，整体回滚
            int rows = accountMapper.changeBalance(BookContext.getBookId(), id, delta, delta.abs());
            if (rows == 0) {
                throw BusinessException.ofKey("erp.fin.opening-adjust-negative");
            }
        }
    }

    @Override
    public void delete(Long id) {
        requireAccount(id);
        Long flows = flowMapper.selectCount(Wrappers.lambdaQuery(FinAccountFlow.class)
                .eq(FinAccountFlow::getBookId, BookContext.getBookId())
                .eq(FinAccountFlow::getAccountId, id));
        if (flows > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.fin.account-has-logs-no-delete");
        }
        accountMapper.deleteById(id);
    }

    @Override
    public AccountBalanceVO balance(Long id) {
        FinAccount account = requireAccount(id);
        Long bookId = BookContext.getBookId();
        BigDecimal income = nz(flowMapper.sumAmountByDirection(bookId, id, "INCOME"));
        BigDecimal expense = nz(flowMapper.sumAmountByDirection(bookId, id, "EXPENSE"));
        return new AccountBalanceVO(id, account.getAccountNo(), account.getName(),
                account.getInitialBalance(), income, expense, account.getCurrentBalance());
    }

    @Override
    public PageResult<AccountFlowVO> flows(Long id, long pageNum, long pageSize) {
        requireAccount(id);
        return com.erp.framework.mybatis.PageUtils.of(flowMapper,
                Wrappers.lambdaQuery(FinAccountFlow.class)
                        .eq(FinAccountFlow::getBookId, BookContext.getBookId())
                        .eq(FinAccountFlow::getAccountId, id)
                        .orderByDesc(FinAccountFlow::getId),
                pageNum, pageSize, AccountFlowVO::from);
    }

    /** 账户必须存在且属于当前账套 */
    private FinAccount requireAccount(Long id) {
        FinAccount account = accountMapper.selectById(id);
        if (account == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.fund-account-not-exists2", id);
        }
        BookContext.checkBookOwnership(account.getBookId(),
                "资金账户[%s %s]".formatted(account.getAccountNo(), account.getName()));
        return account;
    }

    /** 账户编号唯一性校验（账套内唯一） */
    private void checkAccountNoUnique(String accountNo, Long excludeId) {
        Long count = accountMapper.selectCount(Wrappers.lambdaQuery(FinAccount.class)
                .eq(FinAccount::getBookId, BookContext.getBookId())
                .eq(FinAccount::getAccountNo, accountNo)
                .ne(excludeId != null, FinAccount::getId, excludeId));
        if (count > 0) {
            throw BusinessException.ofKey(ErrorCode.DUPLICATE_DATA, "erp.fin.account-no-exists", accountNo);
        }
    }

    /** 表单字段拷贝到实体（不动 currentBalance） */
    private void applyForm(FinAccount account, AccountForm form) {
        account.setAccountNo(form.accountNo());
        account.setName(form.name());
        account.setType(form.type());
        account.setBankName(form.bankName());
        account.setBankAccountNo(form.bankAccountNo());
        account.setInitialBalance(form.initialBalance());
        account.setStatus(form.status() == null ? 1 : form.status());
        account.setRemark(form.remark());
    }

    /** null 转 0 */
    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
