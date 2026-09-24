package com.erp.budget.service;

import com.erp.budget.dto.form.BudgetUsageRecordForm;
import com.erp.budget.dto.vo.BudgetCheckResult;

import java.math.BigDecimal;

/**
 * 预算控制 Service —— 预算模块对其他模块暴露的核心能力（跨模块 API）。
 * <p>
 * <b>对外签名（稳定约定，finance 等模块按此接线，详见 docs/modules/budget.md）：</b>
 * <pre>{@code
 * // 付款/费用发生前校验（超预算按方案配置 WARN 放行或 BLOCK 抛 BusinessException）
 * BudgetCheckResult check(Long deptId, Long subjectId, Integer year, Integer month, BigDecimal amount);
 *
 * // 登记预算执行数（付款单创建/审核时推式记账；冲销传负数金额）
 * Long record(BudgetUsageRecordForm form);
 *
 * // 查询已用预算（凭证口径 + 登记口径合计；只读不校验）
 * BigDecimal getUsedAmount(Long deptId, Long subjectId, Integer year, Integer month);
 * }</pre>
 * <p>
 * <b>已用预算口径：</b>已用 = 凭证口径 + 登记口径。
 * <ul>
 *   <li>凭证口径（只读约定表）：fin_voucher(POSTED) × fin_voucher_entry(该科目借方)
 *       × 制单人部门(sys_user.dept_id)，按年度+月份汇总；</li>
 *   <li>登记口径（本模块 budget_usage_record）：fin_payment 无部门/科目列，
 *       付款类执行数由业务方调 record() 推入（本期接线约定，主线程后续接线）。</li>
 * </ul>
 * <b>控制规则：</b>占用率 = (已用 + 本次金额) / 当月预算 × 100%；
 * 占用率 ≥ 方案配置阈值（budget_control_config.over_percent，默认 100）时——
 * WARN：放行并在结果中给警告文案；BLOCK：抛 BusinessException 阻止业务。
 * 无已下达方案或该部门×科目当月无预算额度时不控制（放行，结果中注明）。
 */
public interface BudgetControlService {

    /**
     * 预算占用校验（对外核心方法）。
     * <p>
     * 定位该年度已下达(RELEASED)预算方案，取 部门×科目×月份 的当月预算，
     * 与已用数（凭证口径 + 登记口径）合并计算本次占用率并按控制配置裁决。
     *
     * @param deptId    部门 ID（sys_dept.id）
     * @param subjectId 科目 ID（fin_subject.id）
     * @param year      年度（用于定位预算方案与汇总期间）
     * @param month     月份 1-12
     * @param amount    本次发生金额（>0）
     * @return 校验结果（level=OK 预算内 / WARN 超阈值警告放行）
     * @throws com.erp.common.exception.BusinessException 超阈值且控制方式为 BLOCK 时抛出（阻止业务）
     */
    BudgetCheckResult check(Long deptId, Long subjectId, Integer year, Integer month, BigDecimal amount);

    /**
     * 登记预算执行数（推式记账）。
     * <p>
     * 接线约定：finance 模块付款单（fin_payment）创建/审核成功后，按付款归属的
     * 部门×费用科目×期间调用本方法落一条 PAYMENT 记录；红冲/退款用负数金额。
     *
     * @param form 执行数登记表单（部门/科目/年月/金额/来源必填）
     * @return 登记记录 ID
     * @throws com.erp.common.exception.BusinessException 部门/科目不存在或科目非费用成本类时抛出
     */
    Long record(BudgetUsageRecordForm form);

    /**
     * 查询已用预算（只读）：凭证口径 + 登记口径 合计。
     *
     * @param deptId    部门 ID
     * @param subjectId 科目 ID
     * @param year      年度
     * @param month     月份 1-12
     * @return 已用金额（无发生返回 0，两位小数）
     */
    BigDecimal getUsedAmount(Long deptId, Long subjectId, Integer year, Integer month);
}
