package com.erp.hr.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.hr.dto.vo.AttendanceSummaryVO;
import com.erp.hr.dto.vo.AttendanceVO;
import com.erp.hr.dto.vo.PieceWageDetailVO;
import com.erp.hr.dto.vo.PieceWageEmployeeSummaryVO;
import com.erp.hr.dto.vo.PieceWageVO;
import com.erp.hr.dto.vo.WageSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 人力资源模块报表/聚合 Mapper —— hr_ 前缀自有表的跨表查询与考勤导入 upsert，
 * SQL 全部在 resources/mapper/hr/HrStatMapper.xml。
 */
@Mapper
public interface HrStatMapper {

    /**
     * 考勤导入单行写入：uk(employee_id, att_date) 冲突时覆盖状态/备注并复活逻辑删除行。
     *
     * @param userId 操作人（写审计字段）
     */
    int upsertAttendance(@Param("employeeId") Long employeeId,
                         @Param("attDate") LocalDate attDate,
                         @Param("status") String status,
                         @Param("remark") String remark,
                         @Param("userId") Long userId);

    /**
     * 物理删除某期间全部计件明细（归集"整单重建"专用）。
     * <p>
     * 明细为可再生的派生数据，且 uk(period_id, work_order_id) 会被逻辑删除行占用导致
     * 重建时唯一键冲突，故此处用物理 DELETE；仅 OPEN 期间可触发（CLOSED 后拒绝归集），
     * 已结账期间数据不受影响。
     */
    int deletePeriodDetails(@Param("periodId") Long periodId);

    /** 考勤明细分页（带员工/部门回显） */
    IPage<AttendanceVO> pageAttendance(IPage<AttendanceVO> page,
                                       @Param("employeeId") Long employeeId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    /** 考勤月度汇总：按员工统计各状态天数 */
    List<AttendanceSummaryVO> attendanceSummary(@Param("month") String month);

    /** 计件期间分页（附明细数/合计金额） */
    IPage<PieceWageVO> pagePieceWages(IPage<PieceWageVO> page);

    /** 计件明细分页（带员工/订单/物料回显） */
    IPage<PieceWageDetailVO> pagePieceWageDetails(IPage<PieceWageDetailVO> page,
                                                  @Param("periodId") Long periodId,
                                                  @Param("employeeId") Long employeeId);

    /** 计件按员工合计 */
    List<PieceWageEmployeeSummaryVO> pieceWageEmployeeSummary(@Param("periodId") Long periodId);

    /**
     * 工资汇总底稿：员工月薪基准 + 当月考勤聚合（加班/缺勤天数）+ 当月计件合计，
     * 加班费/缺勤扣款/应发在 Java 侧按 DECIMAL HALF_UP 计算。
     */
    List<WageSummaryVO> wageSummaryRows(@Param("month") String month);
}
