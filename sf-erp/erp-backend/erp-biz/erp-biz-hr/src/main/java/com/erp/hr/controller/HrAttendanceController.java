package com.erp.hr.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.hr.dto.vo.AttendanceImportResultVO;
import com.erp.hr.dto.vo.AttendanceSummaryVO;
import com.erp.hr.dto.vo.AttendanceVO;
import com.erp.hr.service.HrAttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 考勤接口：CSV 批量导入（同员工同日覆盖）、月度汇总、明细分页。
 */
@Tag(name = "人力资源-考勤")
@RestController
@RequestMapping("/hr/attendances")
@RequiredArgsConstructor
public class HrAttendanceController {

    private final HrAttendanceService attendanceService;

    /**
     * CSV 批量导入：每行"工号,日期(yyyy-MM-dd),状态"，重复行覆盖（按月重复导入幂等）。
     */
    @Operation(summary = "CSV 批量导入考勤")
    @RequirePerm("hr:attendance:add")
    @OperLog(module = "人力资源", operation = "导入考勤")
    @Idempotent
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<AttendanceImportResultVO> importCsv(@Valid @RequestPart("file") MultipartFile file) {
        return Result.ok(attendanceService.importCsv(file));
    }

    /** 月度汇总：按员工统计正常/缺勤/迟到/请假/加班天数 */
    @Operation(summary = "考勤月度汇总")
    @RequirePerm("hr:attendance:list")
    @GetMapping("/summary")
    public Result<List<AttendanceSummaryVO>> summary(@RequestParam String month) {
        return Result.ok(attendanceService.summary(month));
    }

    /** 考勤明细分页（员工/日期区间过滤） */
    @Operation(summary = "考勤明细分页")
    @RequirePerm("hr:attendance:list")
    @GetMapping
    public Result<PageResult<AttendanceVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(attendanceService.page(pageNum, pageSize, employeeId, from, to));
    }
}
