package com.erp.equipment.service;

import com.erp.common.result.PageResult;
import com.erp.equipment.dto.form.PlanExecuteForm;
import com.erp.equipment.dto.form.PlanForm;
import com.erp.equipment.dto.request.PlanQuery;
import com.erp.equipment.dto.vo.PlanVO;

import java.util.List;

/**
 * 保养计划 Service —— 计划维护、到期预警与执行登记。
 */
public interface EqpMaintainPlanService {

    /**
     * 计划分页（含设备信息与到期标记）。
     *
     * @param query 查询条件（deviceId/dueOnly）
     * @return 分页结果
     */
    PageResult<PlanVO> page(PlanQuery query);

    /**
     * 到期清单：已到期 + days 天内到期（含已逾期，按到期日升序）。
     *
     * @param days 提前预警天数（默认 7）
     * @return 到期计划列表
     */
    List<PlanVO> duePlans(int days);

    /**
     * 新增计划（nextDate = lastDate + cycleDays 服务端计算）。
     *
     * @param form 计划表单
     * @return 新计划 ID
     * @throws com.erp.common.exception.BusinessException 设备不存在或已报废
     */
    Long create(PlanForm form);

    /**
     * 修改计划（重算 nextDate）。
     *
     * @param id   计划 ID
     * @param form 计划表单
     */
    void update(Long id, PlanForm form);

    /**
     * 删除计划（逻辑删除，历史执行记录保留）。
     *
     * @param id 计划 ID
     */
    void delete(Long id);

    /**
     * 执行一次保养：写执行记录，计划顺延一个周期（last=今天，next=今天+周期）。
     *
     * @param id   计划 ID
     * @param form 执行人/内容（均可空）
     * @throws com.erp.common.exception.BusinessException 计划不存在
     */
    void execute(Long id, PlanExecuteForm form);
}
