package com.erp.hr.service;

import com.erp.common.result.PageResult;
import com.erp.hr.dto.vo.AttendanceImportResultVO;
import com.erp.hr.dto.vo.AttendanceSummaryVO;
import com.erp.hr.dto.vo.AttendanceVO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 考勤服务 —— CSV 批量导入（同员工同日覆盖）、月度汇总、明细分页。
 */
public interface HrAttendanceService {

    /**
     * CSV 批量导入考勤。
     * <p>
     * 格式：每行三列 {@code 工号,日期(yyyy-MM-dd),状态(NORMAL/ABSENT/LATE/LEAVE/OVERTIME)}，
     * 首行可为表头（含"工号"字样自动跳过）；同员工同日重复行后值覆盖前值（按月重复导入幂等）。
     *
     * @param file 上传的 CSV 文件（UTF-8）
     * @return 总行数/成功/失败与逐行错误
     */
    AttendanceImportResultVO importCsv(MultipartFile file);

    /**
     * 月度汇总：按员工统计正常/缺勤/迟到/请假/加班天数。
     *
     * @param month 月份（yyyy-MM）
     */
    List<AttendanceSummaryVO> summary(String month);

    /**
     * 考勤明细分页。
     *
     * @param employeeId 员工过滤（可空）
     * @param from       起始日期（可空）
     * @param to         截止日期（可空）
     */
    PageResult<AttendanceVO> page(long pageNum, long pageSize, Long employeeId, LocalDate from, LocalDate to);
}
