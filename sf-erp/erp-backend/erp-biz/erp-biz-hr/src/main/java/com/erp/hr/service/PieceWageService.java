package com.erp.hr.service;

import com.erp.common.result.PageResult;
import com.erp.hr.dto.vo.PieceWageCollectResultVO;
import com.erp.hr.dto.vo.PieceWageDetailVO;
import com.erp.hr.dto.vo.PieceWageEmployeeSummaryVO;
import com.erp.hr.dto.vo.PieceWageVO;

import java.util.List;

/**
 * 计件工资服务 —— 期间管理、按价目表+完工订单归集、结账、明细/汇总查询。
 * <p>
 * 归集口径（一期报工无工序粒度时的简化，详见 docs/modules/hr.md）：
 * 完工订单(FINISHED/CLOSED, finish_qty&gt;0, 月份=update_time 所在月) ×
 * 成品物料首条启用价目 → 明细行（数量=finish_qty，金额=数量×单价）；
 * 员工归属 = 订单制单人(create_by) 关联的 hr_employee.user_id。
 */
public interface PieceWageService {

    /** 期间分页（附明细数/合计金额） */
    PageResult<PieceWageVO> page(long pageNum, long pageSize);

    /** 期间详情（不存在抛业务异常） */
    PieceWageVO detail(String period);

    /**
     * 按期间归集计件明细（期间不存在则自动开账；幂等：清空本期间旧明细后重建）。
     *
     * @throws com.erp.common.exception.BusinessException 期间已结账(CLOSED)时拒绝
     */
    PieceWageCollectResultVO collect(String period);

    /**
     * 期间结账：OPEN → CLOSED，结账后拒绝再归集（单向）。
     */
    void close(String period);

    /** 期间明细分页（可按员工过滤） */
    PageResult<PieceWageDetailVO> details(String period, Long employeeId, long pageNum, long pageSize);

    /** 期间按员工合计 */
    List<PieceWageEmployeeSummaryVO> employeeSummary(String period);
}
