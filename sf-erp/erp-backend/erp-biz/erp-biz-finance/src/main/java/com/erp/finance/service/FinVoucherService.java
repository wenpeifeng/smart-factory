package com.erp.finance.service;

import com.erp.common.result.PageResult;
import com.erp.finance.dto.form.VoucherForm;
import com.erp.finance.dto.request.VoucherQuery;
import com.erp.finance.dto.vo.SubjectSummaryVO;
import com.erp.finance.dto.vo.TrialBalanceVO;
import com.erp.finance.dto.vo.VoucherDetailVO;
import com.erp.finance.dto.vo.VoucherVO;

import java.time.LocalDate;

/**
 * 总账凭证服务：手工凭证录入 / 审核（过账）/ 科目汇总 / 试算平衡。
 * <p>
 * 核心不变量：
 * <ul>
 *   <li>凭证保存前借贷必须平衡（BigDecimal.compareTo 精确比较）；</li>
 *   <li>状态机单向 DRAFT → POSTED，过账后禁止修改与删除；</li>
 *   <li>分录科目必须为启用状态的末级科目。</li>
 * </ul>
 */
public interface FinVoucherService {

    /**
     * 分页查询凭证。
     *
     * @param query    查询条件（凭证号/状态/日期区间）
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 凭证分页
     */
    PageResult<VoucherVO> page(VoucherQuery query, long pageNum, long pageSize);

    /**
     * 凭证明细（含分录行，按行号升序）。
     *
     * @param id 凭证 ID
     * @return 凭证明细 VO
     * @throws com.erp.common.exception.BusinessException 凭证不存在
     */
    VoucherDetailVO detail(Long id);

    /**
     * 新增凭证（借贷平衡校验）。
     *
     * @param form 凭证表单
     * @return 凭证 ID
     * @throws com.erp.common.exception.BusinessException 分录不足两行 / 借贷单边校验失败 /
     *           科目不存在、停用或非末级 / 借贷不平衡
     */
    Long create(VoucherForm form);

    /**
     * 修改凭证（仅 DRAFT 状态可改，重新走全套校验，分录整体重写）。
     *
     * @param id   凭证 ID
     * @param form 凭证表单
     * @throws com.erp.common.exception.BusinessException 凭证不存在 / 已过账禁止修改 / 校验失败
     */
    void update(Long id, VoucherForm form);

    /**
     * 删除凭证（仅 DRAFT 状态可删，主表与分录一并逻辑删除）。
     *
     * @param id 凭证 ID
     * @throws com.erp.common.exception.BusinessException 凭证不存在 / 已过账禁止删除
     */
    void delete(Long id);

    /**
     * 审核凭证：DRAFT → POSTED（过账后禁改禁删）。
     *
     * @param id 凭证 ID
     * @throws com.erp.common.exception.BusinessException 凭证不存在 / 已过账无需重复审核
     */
    void audit(Long id);

    /**
     * 科目汇总表：单科目（或全部科目合计）区间内借方合计/贷方合计/期末余额。
     * 只统计已过账凭证。
     *
     * @param subjectId 科目 ID（null = 全部科目合计）
     * @param from      区间起（含，null 默认当年 1 月 1 日）
     * @param to        区间止（含，null 默认当年 12 月 31 日）
     * @return 科目汇总 VO
     * @throws com.erp.common.exception.BusinessException 科目不存在
     */
    SubjectSummaryVO summary(Long subjectId, LocalDate from, LocalDate to);

    /**
     * 试算平衡表：全科目六栏（期初/本期/期末 × 借/贷）+ 合计 + 是否平衡。
     * 只统计已过账凭证。
     *
     * @param from 期间起（含，null 默认当年 1 月 1 日）
     * @param to   期间止（含，null 默认当年 12 月 31 日）
     * @return 试算平衡 VO
     */
    TrialBalanceVO trialBalance(LocalDate from, LocalDate to);
}
