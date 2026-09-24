package com.erp.equipment.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.equipment.dto.form.PlanExecuteForm;
import com.erp.equipment.dto.form.PlanForm;
import com.erp.equipment.dto.request.PlanQuery;
import com.erp.equipment.dto.vo.PlanVO;
import com.erp.equipment.entity.EqpDevice;
import com.erp.equipment.entity.EqpMaintainPlan;
import com.erp.equipment.entity.EqpMaintainRecord;
import com.erp.equipment.mapper.EqpDeviceMapper;
import com.erp.equipment.mapper.EqpMaintainPlanMapper;
import com.erp.equipment.mapper.EqpMaintainRecordMapper;
import com.erp.equipment.service.EqpMaintainPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 保养计划服务实现。
 * <p>
 * 核心规则：
 * <ul>
 *   <li>nextDate = lastDate + cycleDays，只在创建/修改/执行三个入口计算；</li>
 *   <li>执行 = 写一条 eqp_maintain_record + 计划顺延一个周期（last=今天，next=今天+周期），
 *       两写同一事务；</li>
 *   <li>到期清单按 next_date &lt;= CURDATE()+days（含已逾期），供 workflow 预警直查。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EqpMaintainPlanServiceImpl implements EqpMaintainPlanService {

    private final EqpMaintainPlanMapper planMapper;
    private final EqpMaintainRecordMapper recordMapper;
    private final EqpDeviceMapper deviceMapper;
    private final EqpDeviceServiceImpl deviceService;

    @Override
    public PageResult<PlanVO> page(PlanQuery query) {
        IPage<PlanVO> page = planMapper.selectPlanPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        page.getRecords().forEach(this::fillDueInfo);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<PlanVO> duePlans(int days) {
        List<PlanVO> due = planMapper.selectDuePlans(days);
        due.forEach(this::fillDueInfo);
        return due;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PlanForm form) {
        EqpDevice device = deviceService.requireDevice(form.getDeviceId());
        if (device.getStatus() == EqpDevice.STATUS_SCRAPPED) {
            throw BusinessException.ofKey("erp.equipment.scrapped-no-maintain-plan", device.getCode());
        }
        EqpMaintainPlan plan = new EqpMaintainPlan();
        applyForm(plan, form);
        planMapper.insert(plan);
        return plan.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PlanForm form) {
        requirePlan(id);
        deviceService.requireDevice(form.getDeviceId());
        EqpMaintainPlan plan = new EqpMaintainPlan();
        applyForm(plan, form);
        plan.setId(id);
        planMapper.updateById(plan);
    }

    @Override
    public void delete(Long id) {
        requirePlan(id);
        planMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long id, PlanExecuteForm form) {
        EqpMaintainPlan plan = requirePlan(id);
        EqpDevice device = deviceService.requireDevice(plan.getDeviceId());
        LocalDate today = LocalDate.now();

        // 执行记录（冗余设备 ID，便于按设备回溯保养历史）
        EqpMaintainRecord record = new EqpMaintainRecord();
        record.setPlanId(id);
        record.setDeviceId(plan.getDeviceId());
        record.setExecuteTime(LocalDateTime.now());
        record.setExecutor(form != null && form.getExecutor() != null && !form.getExecutor().isBlank()
                ? form.getExecutor() : "系统");
        record.setContent(form != null ? form.getContent() : null);
        recordMapper.insert(record);

        // 计划顺延一个周期：上次保养=今天，下次=今天+周期
        EqpMaintainPlan next = new EqpMaintainPlan();
        next.setId(id);
        next.setLastDate(today);
        next.setNextDate(today.plusDays(plan.getCycleDays()));
        planMapper.updateById(next);

        // 保养完成视为设备可用：维修中 → 运行（其余状态不动）
        if (device.getStatus() != null && device.getStatus() == EqpDevice.STATUS_REPAIRING) {
            EqpDevice patch = new EqpDevice();
            patch.setId(device.getId());
            patch.setStatus(EqpDevice.STATUS_RUNNING);
            deviceMapper.updateById(patch);
        }
    }

    /** 表单 → 实体，并重算 nextDate（不变量：next = last + cycle） */
    private void applyForm(EqpMaintainPlan plan, PlanForm form) {
        plan.setDeviceId(form.getDeviceId());
        plan.setMaintainType(form.getMaintainType());
        plan.setCycleDays(form.getCycleDays());
        plan.setLastDate(form.getLastDate());
        // lastDate 为空（尚未保养过的新设备）：以今天为周期起点
        LocalDate base = form.getLastDate() != null ? form.getLastDate() : LocalDate.now();
        plan.setNextDate(base.plusDays(form.getCycleDays()));
        plan.setRemark(form.getRemark());
    }

    /** 填充到期标记：daysLeft（负=已逾期天数）、overdue、类型中文 */
    private void fillDueInfo(PlanVO vo) {
        vo.setMaintainTypeLabel(PlanVO.typeLabelOf(vo.getMaintainType()));
        if (vo.getNextDate() == null) {
            return;
        }
        long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), vo.getNextDate());
        vo.setDaysLeft(daysLeft);
        vo.setOverdue(daysLeft < 0);
    }

    private EqpMaintainPlan requirePlan(Long id) {
        EqpMaintainPlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw BusinessException.ofKey("erp.equipment.maintain-plan-not-exists", id);
        }
        return plan;
    }
}
