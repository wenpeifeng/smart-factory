package com.erp.budget.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.budget.dto.form.BudgetControlConfigForm;
import com.erp.budget.dto.form.BudgetMatrixForm;
import com.erp.budget.dto.form.BudgetPlanForm;
import com.erp.budget.dto.query.BudgetPlanQuery;
import com.erp.budget.dto.vo.BudgetMatrixVO;
import com.erp.budget.dto.vo.BudgetOptionsVO;
import com.erp.budget.dto.vo.BudgetPlanVO;
import com.erp.budget.entity.BudgetControlConfig;
import com.erp.budget.entity.BudgetItem;
import com.erp.budget.entity.BudgetPlan;
import com.erp.budget.mapper.BudgetControlConfigMapper;
import com.erp.budget.mapper.BudgetItemMapper;
import com.erp.budget.mapper.BudgetPlanMapper;
import com.erp.budget.mapper.CrossModuleMapper;
import com.erp.budget.service.BudgetPlanService;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.framework.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 预算方案 Service 实现。
 * <p>
 * 关键约束：
 * <ul>
 *   <li>状态机单向 DRAFT → RELEASED → CLOSED，编辑/删除仅 DRAFT，关闭仅 RELEASED；</li>
 *   <li>矩阵保存整体提交：单元格 upsert（ON DUPLICATE KEY 顺带复活逻辑删行，
 *       避开 uk 四元组与逻辑删冲突），未提交的部门×科目组合逻辑删；</li>
 *   <li>总额冗余在 budget_plan.total_amount，矩阵保存后重算；</li>
 *   <li>科目仅允许末级费用/成本类（fin_subject 约定表只读校验）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BudgetPlanServiceImpl implements BudgetPlanService {

    private final BudgetPlanMapper planMapper;
    private final BudgetItemMapper itemMapper;
    private final BudgetControlConfigMapper configMapper;
    private final CrossModuleMapper crossModuleMapper;

    @Override
    public PageResult<BudgetPlanVO> page(BudgetPlanQuery query) {
        var page = planMapper.selectPlanPage(new Page<>(query.getPageNum(), query.getPageSize()), query);
        page.getRecords().forEach(vo -> vo.setStatusText(statusText(vo.getStatus())));
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public BudgetPlanVO getDetail(Long id) {
        BudgetPlan plan = requirePlan(id);
        BudgetPlanVO vo = toVO(plan);
        BudgetControlConfig config = configMapper.selectByPlanId(id);
        if (config != null) {
            vo.setControlWay(config.getControlWay());
            vo.setOverPercent(config.getOverPercent());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(BudgetPlanForm form) {
        requireCodeAvailable(form.getPlanCode(), null);
        BudgetPlan plan = new BudgetPlan();
        plan.setPlanCode(form.getPlanCode());
        plan.setPlanName(form.getPlanName());
        plan.setBudgetYear(form.getBudgetYear());
        plan.setStatus(BudgetPlan.STATUS_DRAFT);
        plan.setTotalAmount(BigDecimal.ZERO);
        plan.setPrepareBy(SecurityUtils.getCurrentUserId());
        plan.setRemark(form.getRemark());
        planMapper.insert(plan);

        // 每方案一条默认控制配置（WARN 警告放行 / 100%），下达前后可调
        BudgetControlConfig config = new BudgetControlConfig();
        config.setPlanId(plan.getId());
        config.setControlWay(BudgetControlConfig.WAY_WARN);
        config.setOverPercent(BudgetControlConfig.DEFAULT_OVER_PERCENT);
        configMapper.insert(config);
        return plan.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, BudgetPlanForm form) {
        BudgetPlan plan = requirePlan(id);
        requireDraft(plan);
        requireCodeAvailable(form.getPlanCode(), id);
        BudgetPlan update = new BudgetPlan();
        update.setId(id);
        update.setPlanCode(form.getPlanCode());
        update.setPlanName(form.getPlanName());
        update.setBudgetYear(form.getBudgetYear());
        update.setRemark(form.getRemark());
        planMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BudgetPlan plan = requirePlan(id);
        requireDraft(plan);
        planMapper.deleteById(id);
        itemMapper.delete(Wrappers.lambdaQuery(BudgetItem.class).eq(BudgetItem::getPlanId, id));
        configMapper.delete(Wrappers.lambdaQuery(BudgetControlConfig.class)
                .eq(BudgetControlConfig::getPlanId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long id) {
        BudgetPlan plan = requirePlan(id);
        requireStatus(plan, BudgetPlan.STATUS_DRAFT, "仅草稿状态方案可下达");
        Long cellCount = itemMapper.selectCount(Wrappers.lambdaQuery(BudgetItem.class)
                .eq(BudgetItem::getPlanId, id));
        if (cellCount == null || cellCount == 0) {
            throw BusinessException.ofKey("erp.budget.scheme-no-details-no-issue", plan.getPlanCode(), plan.getPlanName());
        }
        BudgetPlan update = new BudgetPlan();
        update.setId(id);
        update.setStatus(BudgetPlan.STATUS_RELEASED);
        planMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        BudgetPlan plan = requirePlan(id);
        requireStatus(plan, BudgetPlan.STATUS_RELEASED, "仅已下达方案可关闭");
        BudgetPlan update = new BudgetPlan();
        update.setId(id);
        update.setStatus(BudgetPlan.STATUS_CLOSED);
        planMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copyPlan(Long id, Integer targetYear) {
        BudgetPlan source = requirePlan(id);
        int year = targetYear == null ? source.getBudgetYear() + 1 : targetYear;
        if (year < 2000 || year > 2100) {
            throw BusinessException.ofKey("erp.budget.year-invalid", year);
        }
        // 编码在来源编码基础上追加后缀，冲突时递增序号
        String code = source.getPlanCode() + "-C";
        String candidate = code;
        int seq = 1;
        while (planMapper.selectCount(Wrappers.lambdaQuery(BudgetPlan.class)
                .eq(BudgetPlan::getPlanCode, candidate)) > 0) {
            candidate = code + seq++;
        }

        BudgetPlan target = new BudgetPlan();
        target.setPlanCode(candidate);
        target.setPlanName(source.getPlanName() + "(复制)");
        target.setBudgetYear(year);
        target.setStatus(BudgetPlan.STATUS_DRAFT);
        target.setTotalAmount(source.getTotalAmount());
        target.setPrepareBy(SecurityUtils.getCurrentUserId());
        target.setRemark(source.getRemark());
        planMapper.insert(target);

        BudgetControlConfig sourceConfig = configMapper.selectByPlanId(id);
        BudgetControlConfig config = new BudgetControlConfig();
        config.setPlanId(target.getId());
        config.setControlWay(sourceConfig == null ? BudgetControlConfig.WAY_WARN : sourceConfig.getControlWay());
        config.setOverPercent(sourceConfig == null
                ? BudgetControlConfig.DEFAULT_OVER_PERCENT : sourceConfig.getOverPercent());
        configMapper.insert(config);

        List<BudgetItem> sourceItems = itemMapper.selectList(Wrappers.lambdaQuery(BudgetItem.class)
                .eq(BudgetItem::getPlanId, id));
        Long userId = SecurityUtils.getCurrentUserId();
        for (BudgetItem cell : sourceItems) {
            BudgetItem copy = new BudgetItem();
            copy.setPlanId(target.getId());
            copy.setDeptId(cell.getDeptId());
            copy.setSubjectId(cell.getSubjectId());
            copy.setMonth(cell.getMonth());
            copy.setAmount(cell.getAmount());
            copy.setCreateBy(userId);
            copy.setUpdateBy(userId);
            itemMapper.insert(copy);
        }
        return target.getId();
    }

    @Override
    public BudgetMatrixVO getMatrix(Long id) {
        BudgetPlan plan = requirePlan(id);
        List<BudgetItem> cells = itemMapper.selectList(Wrappers.lambdaQuery(BudgetItem.class)
                .eq(BudgetItem::getPlanId, id)
                .orderByAsc(BudgetItem::getDeptId)
                .orderByAsc(BudgetItem::getSubjectId));

        Map<Long, String> deptNames = crossModuleMapper.listDepts().stream()
                .collect(Collectors.toMap(CrossModuleMapper.DeptRef::getId,
                        CrossModuleMapper.DeptRef::getName, (a, b) -> a));
        Map<Long, CrossModuleMapper.SubjectRef> subjects = subjectRefMap();

        // 单元格按 部门×科目 聚合成行
        Map<String, BudgetMatrixVO.MatrixRowVO> rowMap = new LinkedHashMap<>();
        for (BudgetItem cell : cells) {
            String key = cell.getDeptId() + "-" + cell.getSubjectId();
            BudgetMatrixVO.MatrixRowVO row = rowMap.computeIfAbsent(key, k -> newRow(cell, deptNames, subjects));
            setMonthAmount(row, cell.getMonth(), cell.getAmount());
        }

        BudgetMatrixVO vo = new BudgetMatrixVO();
        vo.setPlanId(id);
        vo.setBudgetYear(plan.getBudgetYear());
        vo.setStatus(plan.getStatus());
        List<BudgetMatrixVO.MatrixRowVO> rows = new ArrayList<>(rowMap.values());
        rows.forEach(this::fillRowTotal);
        vo.setRows(rows);
        fillColumnTotals(vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMatrix(Long id, BudgetMatrixForm form) {
        BudgetPlan plan = requirePlan(id);
        requireDraft(plan);
        validateMatrixRows(form.getRows());

        // 未提交的部门×科目组合 → 逻辑删其全部月份单元格
        Set<String> submittedKeys = form.getRows().stream()
                .map(r -> r.getDeptId() + "-" + r.getSubjectId())
                .collect(Collectors.toSet());
        List<BudgetItem> existing = itemMapper.selectList(Wrappers.lambdaQuery(BudgetItem.class)
                .eq(BudgetItem::getPlanId, id));
        List<Long> removeIds = existing.stream()
                .filter(cell -> !submittedKeys.contains(cell.getDeptId() + "-" + cell.getSubjectId()))
                .map(BudgetItem::getId)
                .toList();
        if (!removeIds.isEmpty()) {
            itemMapper.deleteByIds(removeIds);
        }

        // 单元格 upsert（自定义 SQL 不走 MP 审计填充，手动置操作人）
        Long userId = SecurityUtils.getCurrentUserId();
        BigDecimal total = BigDecimal.ZERO;
        for (BudgetMatrixForm.MatrixRowForm rowForm : form.getRows()) {
            for (int month = 1; month <= 12; month++) {
                BigDecimal amount = monthAmount(rowForm, month);
                if (amount == null) {
                    amount = BigDecimal.ZERO;
                }
                amount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
                total = total.add(amount);
                BudgetItem cell = new BudgetItem();
                cell.setPlanId(id);
                cell.setDeptId(rowForm.getDeptId());
                cell.setSubjectId(rowForm.getSubjectId());
                cell.setMonth(month);
                cell.setAmount(amount);
                cell.setCreateBy(userId);
                cell.setUpdateBy(userId);
                itemMapper.upsertCell(cell);
            }
        }

        // 重算方案总额冗余
        BudgetPlan update = new BudgetPlan();
        update.setId(id);
        update.setTotalAmount(total);
        planMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveControlConfig(Long id, BudgetControlConfigForm form) {
        BudgetPlan plan = requirePlan(id);
        if (BudgetPlan.STATUS_CLOSED.equals(plan.getStatus())) {
            throw BusinessException.ofKey("erp.budget.scheme-closed-no-modify", plan.getPlanCode(), plan.getPlanName());
        }
        BudgetControlConfig config = configMapper.selectByPlanId(id);
        if (config == null) {
            config = new BudgetControlConfig();
            config.setPlanId(id);
            config.setControlWay(form.getControlWay());
            config.setOverPercent(form.getOverPercent());
            configMapper.insert(config);
        } else {
            BudgetControlConfig update = new BudgetControlConfig();
            update.setId(config.getId());
            update.setControlWay(form.getControlWay());
            update.setOverPercent(form.getOverPercent());
            configMapper.updateById(update);
        }
    }

    @Override
    public BudgetOptionsVO getOptions() {
        BudgetOptionsVO vo = new BudgetOptionsVO();
        vo.setDepts(crossModuleMapper.listDepts().stream().map(d -> {
            BudgetOptionsVO.DeptNodeVO node = new BudgetOptionsVO.DeptNodeVO();
            node.setId(d.getId());
            node.setParentId(d.getParentId());
            node.setName(d.getName());
            node.setStatus(d.getStatus());
            return node;
        }).toList());
        vo.setSubjects(crossModuleMapper.listExpenseSubjects().stream().map(s -> {
            BudgetOptionsVO.SubjectNodeVO node = new BudgetOptionsVO.SubjectNodeVO();
            node.setId(s.getId());
            node.setCode(s.getCode());
            node.setName(s.getName());
            return node;
        }).toList());
        return vo;
    }

    // ---------------- 私有工具 ----------------

    /** 加载方案，不存在抛业务异常 */
    private BudgetPlan requirePlan(Long id) {
        BudgetPlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw BusinessException.ofKey("erp.budget.scheme-not-exists", id);
        }
        return plan;
    }

    /** 校验方案为草稿（编辑/删除/编制明细前置条件） */
    private void requireDraft(BudgetPlan plan) {
        requireStatus(plan, BudgetPlan.STATUS_DRAFT, "方案已下达/关闭，禁止修改");
    }

    /** 校验方案处于指定状态 */
    private void requireStatus(BudgetPlan plan, String status, String message) {
        if (!status.equals(plan.getStatus())) {
            throw BusinessException.ofKey("erp.budget.current-status", message, statusText(plan.getStatus()));
        }
    }

    /** 编码唯一性校验（excludeId 非空时排除自身） */
    private void requireCodeAvailable(String code, Long excludeId) {
        Long count = planMapper.selectCount(Wrappers.lambdaQuery(BudgetPlan.class)
                .eq(BudgetPlan::getPlanCode, code)
                .ne(excludeId != null, BudgetPlan::getId, excludeId));
        if (count != null && count > 0) {
            throw BusinessException.ofKey("erp.budget.scheme-code-exists", code);
        }
    }

    /** 矩阵行校验：部门/科目合法（末级费用成本类）且部门×科目不重复 */
    private void validateMatrixRows(List<BudgetMatrixForm.MatrixRowForm> rows) {
        Set<String> keys = new HashSet<>();
        Set<Long> validatedSubjects = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            BudgetMatrixForm.MatrixRowForm row = rows.get(i);
            if (crossModuleMapper.findDept(row.getDeptId()) == null) {
                throw BusinessException.ofKey("erp.budget.line-dept-not-exists", i + 1, row.getDeptId());
            }
            if (!validatedSubjects.contains(row.getSubjectId())) {
                CrossModuleMapper.SubjectRef subject = crossModuleMapper.findSubject(row.getSubjectId());
                if (subject == null) {
                    throw BusinessException.ofKey("erp.budget.line-account-not-exists", i + 1, row.getSubjectId());
                }
                if (crossModuleMapper.countBudgetableSubject(row.getSubjectId()) == 0
                        || crossModuleMapper.countChildSubjects(row.getSubjectId()) > 0) {
                    throw BusinessException.ofKey("erp.budget.line-account-not-leaf", i + 1, subject.getCode(), subject.getName());
                }
                validatedSubjects.add(row.getSubjectId());
            }
            String key = row.getDeptId() + "-" + row.getSubjectId();
            if (!keys.add(key)) {
                throw BusinessException.ofKey("erp.budget.line-dept-account-duplicate", i + 1);
            }
        }
    }

    /** 按月份反射取行金额（避免 12 个 if 的样板） */
    private BigDecimal monthAmount(BudgetMatrixForm.MatrixRowForm row, int month) {
        try {
            var field = BudgetMatrixForm.MatrixRowForm.class.getDeclaredField("m" + month);
            field.setAccessible(true);
            return (BigDecimal) field.get(row);
        } catch (ReflectiveOperationException e) {
            throw BusinessException.ofKey("erp.budget.month-invalid", month);
        }
    }

    /** 构造矩阵行并回填部门/科目名称 */
    private BudgetMatrixVO.MatrixRowVO newRow(BudgetItem cell, Map<Long, String> deptNames,
                                              Map<Long, CrossModuleMapper.SubjectRef> subjects) {
        BudgetMatrixVO.MatrixRowVO row = new BudgetMatrixVO.MatrixRowVO();
        row.setDeptId(cell.getDeptId());
        row.setDeptName(deptNames.getOrDefault(cell.getDeptId(), "部门#" + cell.getDeptId()));
        CrossModuleMapper.SubjectRef subject = subjects.get(cell.getSubjectId());
        row.setSubjectId(cell.getSubjectId());
        if (subject != null) {
            row.setSubjectCode(subject.getCode());
            row.setSubjectName(subject.getName());
        } else {
            row.setSubjectCode("科目#" + cell.getSubjectId());
            row.setSubjectName("科目#" + cell.getSubjectId());
        }
        return row;
    }

    /** 行内按月赋值 */
    private void setMonthAmount(BudgetMatrixVO.MatrixRowVO row, Integer month, BigDecimal amount) {
        try {
            var field = BudgetMatrixVO.MatrixRowVO.class.getDeclaredField("m" + month);
            field.setAccessible(true);
            field.set(row, amount == null ? BigDecimal.ZERO : amount);
        } catch (ReflectiveOperationException e) {
            throw BusinessException.ofKey("erp.budget.month-invalid", month);
        }
    }

    /** 读取行内某月金额 */
    private BigDecimal rowMonthAmount(BudgetMatrixVO.MatrixRowVO row, int month) {
        try {
            var field = BudgetMatrixVO.MatrixRowVO.class.getDeclaredField("m" + month);
            field.setAccessible(true);
            BigDecimal value = (BigDecimal) field.get(row);
            return value == null ? BigDecimal.ZERO : value;
        } catch (ReflectiveOperationException e) {
            throw BusinessException.ofKey("erp.budget.month-invalid", month);
        }
    }

    /** 行合计 */
    private void fillRowTotal(BudgetMatrixVO.MatrixRowVO row) {
        BigDecimal total = BigDecimal.ZERO;
        for (int m = 1; m <= 12; m++) {
            total = total.add(rowMonthAmount(row, m));
        }
        row.setRowTotal(total);
    }

    /** 列合计 + 总计 */
    private void fillColumnTotals(BudgetMatrixVO vo) {
        BudgetMatrixVO.ColumnTotalVO totals = vo.getColumnTotals();
        for (BudgetMatrixVO.MatrixRowVO row : vo.getRows()) {
            for (int m = 1; m <= 12; m++) {
                try {
                    var field = BudgetMatrixVO.ColumnTotalVO.class.getDeclaredField("m" + m);
                    field.setAccessible(true);
                    BigDecimal current = (BigDecimal) field.get(totals);
                    field.set(totals, current.add(rowMonthAmount(row, m)));
                } catch (ReflectiveOperationException e) {
                    throw BusinessException.ofKey("erp.budget.month-invalid", m);
                }
            }
        }
        BigDecimal grand = BigDecimal.ZERO;
        for (BudgetMatrixVO.MatrixRowVO row : vo.getRows()) {
            grand = grand.add(row.getRowTotal() == null ? BigDecimal.ZERO : row.getRowTotal());
        }
        totals.setGrandTotal(grand);
    }

    /** 科目引用缓存（末级费用成本类优先，兜底单查，防科目后续改类导致名称缺失） */
    private Map<Long, CrossModuleMapper.SubjectRef> subjectRefMap() {
        Map<Long, CrossModuleMapper.SubjectRef> map = new HashMap<>();
        for (CrossModuleMapper.SubjectRef ref : crossModuleMapper.listExpenseSubjects()) {
            map.put(ref.getId(), ref);
        }
        return map;
    }

    /** 状态码 → 文案 */
    private String statusText(String status) {
        return switch (status == null ? "" : status) {
            case BudgetPlan.STATUS_DRAFT -> "草稿";
            case BudgetPlan.STATUS_RELEASED -> "已下达";
            case BudgetPlan.STATUS_CLOSED -> "已关闭";
            default -> status;
        };
    }

    /** Entity → VO */
    private BudgetPlanVO toVO(BudgetPlan plan) {
        BudgetPlanVO vo = new BudgetPlanVO();
        vo.setId(plan.getId());
        vo.setPlanCode(plan.getPlanCode());
        vo.setPlanName(plan.getPlanName());
        vo.setBudgetYear(plan.getBudgetYear());
        vo.setStatus(plan.getStatus());
        vo.setStatusText(statusText(plan.getStatus()));
        vo.setTotalAmount(plan.getTotalAmount());
        vo.setPrepareBy(plan.getPrepareBy());
        vo.setPrepareByName(plan.getPrepareBy() == null
                ? null : crossModuleMapper.selectUserNickname(plan.getPrepareBy()));
        vo.setRemark(plan.getRemark());
        vo.setCreateTime(plan.getCreateTime());
        return vo;
    }
}
