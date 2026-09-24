package com.erp.hr.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.hr.constant.HrConstants;
import com.erp.hr.dto.vo.AttendanceImportResultVO;
import com.erp.hr.dto.vo.AttendanceSummaryVO;
import com.erp.hr.dto.vo.AttendanceVO;
import com.erp.hr.entity.HrEmployee;
import com.erp.hr.mapper.HrEmployeeMapper;
import com.erp.hr.mapper.HrStatMapper;
import com.erp.hr.service.HrAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 考勤服务实现。
 * <p>
 * 导入用 upsert（ON DUPLICATE KEY UPDATE）实现"同员工同日覆盖"，
 * 适配按月重复导入整月考勤；月度汇总/明细走 HrStatMapper 聚合 SQL。
 */
@Service
@RequiredArgsConstructor
public class HrAttendanceServiceImpl implements HrAttendanceService {

    /** 考勤 CSV 合法状态集 */
    private static final Set<String> VALID_STATUS = Set.of(
            HrConstants.ATT_NORMAL, HrConstants.ATT_ABSENT, HrConstants.ATT_LATE,
            HrConstants.ATT_LEAVE, HrConstants.ATT_OVERTIME);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HrEmployeeMapper employeeMapper;
    private final HrStatMapper statMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceImportResultVO importCsv(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int success = 0;
        int total = 0;
        // 预载全部员工工号映射（工号唯一，员工量级远小于导入行数，一次查库避免逐行查询）
        Map<String, HrEmployee> byEmpNo = employeeMapper.selectList(
                        Wrappers.lambdaQuery(HrEmployee.class).select(HrEmployee::getId, HrEmployee::getEmpNo))
                .stream()
                .collect(Collectors.toMap(HrEmployee::getEmpNo, Function.identity(), (a, b) -> a));

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int rowNo = 0;
            while ((line = reader.readLine()) != null) {
                rowNo++;
                if (rowNo == 1) {
                    if (line.startsWith("﻿")) {
                        line = line.substring(1);
                    }
                    // 首行含"工号"视为表头跳过
                    if (line.contains("工号")) {
                        continue;
                    }
                }
                if (line.isBlank()) {
                    continue;
                }
                total++;
                if (importRow(line, rowNo, byEmpNo, errors)) {
                    success++;
                }
            }
            if (rowNo == 0) {
                throw BusinessException.ofKey("erp.hr.csv-empty");
            }
        } catch (IOException e) {
            throw BusinessException.ofKey("erp.hr.csv-read-failed", e.getMessage());
        }
        return new AttendanceImportResultVO(total, success, errors.size(), errors);
    }

    @Override
    public List<AttendanceSummaryVO> summary(String month) {
        requireMonth(month);
        return statMapper.attendanceSummary(month);
    }

    @Override
    public PageResult<AttendanceVO> page(long pageNum, long pageSize, Long employeeId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw BusinessException.ofKey("erp.hr.date-range-order");
        }
        IPage<AttendanceVO> page = statMapper.pageAttendance(new Page<>(pageNum, pageSize), employeeId, from, to);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    // ==================== 私有工具 ====================

    /**
     * 导入单行：工号,日期,状态。
     *
     * @return true 成功；失败时错误信息追加到 errors（不中断整体导入）
     */
    private boolean importRow(String line, int rowNo, Map<String, HrEmployee> byEmpNo, List<String> errors) {
        try {
            String[] fields = line.split(",", -1);
            if (fields.length < 3) {
                throw BusinessException.ofKey("erp.hr.csv-columns");
            }
            String empNo = fields[0].trim();
            String dateStr = fields[1].trim();
            String status = fields[2].trim().toUpperCase();
            String remark = fields.length > 3 ? fields[3].trim() : null;

            HrEmployee employee = byEmpNo.get(empNo);
            if (employee == null) {
                throw BusinessException.ofKey("erp.hr.emp-no-not-exists", empNo);
            }
            if (!VALID_STATUS.contains(status)) {
                throw BusinessException.ofKey("erp.hr.status-invalid", status);
            }
            LocalDate date;
            try {
                date = LocalDate.parse(dateStr, DATE_FMT);
            } catch (Exception e) {
                throw BusinessException.ofKey("erp.hr.date-format-invalid", dateStr);
            }
            statMapper.upsertAttendance(employee.getId(), date, status,
                    remark == null || remark.isBlank() ? null : remark, currentUserId());
            return true;
        } catch (BusinessException e) {
            errors.add("第%d行: %s".formatted(rowNo, e.getMessage()));
            return false;
        }
    }

    /** 月份格式校验（yyyy-MM） */
    private void requireMonth(String month) {
        if (month == null || !month.matches("\\d{4}-\\d{2}")) {
            throw BusinessException.ofKey("erp.hr.month-format-invalid", month);
        }
    }

    /** 当前登录用户 ID（与审计填充器同一口径，未登录返回 0=系统） */
    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return 0L;
    }
}
