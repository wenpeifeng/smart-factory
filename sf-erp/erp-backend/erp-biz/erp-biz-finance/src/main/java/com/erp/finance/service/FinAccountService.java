package com.erp.finance.service;

import com.erp.common.result.PageResult;
import com.erp.finance.dto.form.AccountForm;
import com.erp.finance.dto.vo.AccountBalanceVO;
import com.erp.finance.dto.vo.AccountFlowVO;
import com.erp.finance.dto.vo.AccountVO;

import java.util.List;

/**
 * 资金账户服务：账户 CRUD、余额快照、流水查询。
 * <p>
 * 账户余额的增减只允许由收付款核销通过条件 UPDATE 原子完成。
 */
public interface FinAccountService {

    /**
     * 查询全部账户（按 ID 升序）。
     *
     * @return 账户 VO 列表
     */
    List<AccountVO> list();

    /**
     * 新增账户（当前余额 = 期初余额）。
     *
     * @param form 账户表单
     * @throws com.erp.common.exception.BusinessException 账户编号重复
     */
    void create(AccountForm form);

    /**
     * 修改账户（期初余额变动时差额同步调整当前余额，不允许调为负）。
     *
     * @param id   账户 ID
     * @param form 账户表单
     * @throws com.erp.common.exception.BusinessException 账户不存在 / 编号重复 / 期初调整导致余额为负
     */
    void update(Long id, AccountForm form);

    /**
     * 删除账户（逻辑删除）。已有流水的账户不允许删除。
     *
     * @param id 账户 ID
     * @throws com.erp.common.exception.BusinessException 账户不存在 / 已产生流水
     */
    void delete(Long id);

    /**
     * 账户余额快照：期初 + 累计收入 - 累计支出 与当前余额对账。
     *
     * @param id 账户 ID
     * @return 余额快照 VO
     * @throws com.erp.common.exception.BusinessException 账户不存在
     */
    AccountBalanceVO balance(Long id);

    /**
     * 账户流水分页查询（按 ID 倒序）。
     *
     * @param id       账户 ID
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 流水分页
     */
    PageResult<AccountFlowVO> flows(Long id, long pageNum, long pageSize);
}
