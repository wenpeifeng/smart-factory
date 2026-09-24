package com.erp.budget.service;

import com.erp.budget.dto.form.BudgetControlConfigForm;
import com.erp.budget.dto.form.BudgetMatrixForm;
import com.erp.budget.dto.form.BudgetPlanForm;
import com.erp.budget.dto.query.BudgetPlanQuery;
import com.erp.budget.dto.vo.BudgetMatrixVO;
import com.erp.budget.dto.vo.BudgetOptionsVO;
import com.erp.budget.dto.vo.BudgetPlanVO;
import com.erp.common.result.PageResult;

/**
 * 预算方案 Service —— 方案全生命周期（编制/下达/关闭/复制）与明细矩阵维护。
 * <p>
 * 状态机：DRAFT 草稿(可编制/修改/删除) → RELEASED 已下达(明细锁定禁改) → CLOSED 已关闭。
 */
public interface BudgetPlanService {

    /**
     * 分页查询方案（关键词/年度/状态）。
     */
    PageResult<BudgetPlanVO> page(BudgetPlanQuery query);

    /**
     * 方案详情（含控制配置与编制人姓名）。
     *
     * @throws com.erp.common.exception.BusinessException 方案不存在
     */
    BudgetPlanVO getDetail(Long id);

    /**
     * 新增方案（初始 DRAFT，同时落默认控制配置 WARN/100%）。
     *
     * @return 新方案 ID
     */
    Long create(BudgetPlanForm form);

    /**
     * 修改方案基本信息（仅 DRAFT）。
     *
     * @throws com.erp.common.exception.BusinessException 非 DRAFT 或编码重复
     */
    void update(Long id, BudgetPlanForm form);

    /**
     * 删除方案（仅 DRAFT，连同明细与控制配置逻辑删）。
     *
     * @throws com.erp.common.exception.BusinessException 非 DRAFT
     */
    void delete(Long id);

    /**
     * 下达方案（DRAFT → RELEASED，明细锁定并开始参与控制/分析）。
     *
     * @throws com.erp.common.exception.BusinessException 非 DRAFT 或无明细
     */
    void release(Long id);

    /**
     * 关闭方案（RELEASED → CLOSED，留档不再参与控制）。
     *
     * @throws com.erp.common.exception.BusinessException 非 RELEASED
     */
    void close(Long id);

    /**
     * 复制方案（复制上年/指定年度方案为新一年的 DRAFT，含明细与控制配置）。
     *
     * @param id         来源方案 ID
     * @param targetYear 目标年度（空 = 来源年度 + 1）
     * @return 新方案 ID
     * @throws com.erp.common.exception.BusinessException 来源不存在或目标年度非法
     */
    Long copyPlan(Long id, Integer targetYear);

    /**
     * 读取明细矩阵（部门×科目×12 月 + 行列合计）。
     *
     * @throws com.erp.common.exception.BusinessException 方案不存在
     */
    BudgetMatrixVO getMatrix(Long id);

    /**
     * 保存明细矩阵（仅 DRAFT；整体提交：未出现的部门×科目组合被删除，单元格 upsert）。
     *
     * @throws com.erp.common.exception.BusinessException 非 DRAFT / 部门科目非法 / 行重复
     */
    void saveMatrix(Long id, BudgetMatrixForm form);

    /**
     * 保存控制配置（DRAFT/RELEASED 可调，CLOSED 禁改）。
     *
     * @throws com.erp.common.exception.BusinessException 方案不存在或已关闭
     */
    void saveControlConfig(Long id, BudgetControlConfigForm form);

    /**
     * 编制基础选项：部门列表 + 末级费用/成本类科目列表（约定表只读）。
     */
    BudgetOptionsVO getOptions();
}
