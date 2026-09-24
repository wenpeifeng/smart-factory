package com.erp.hr.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 考勤 CSV 导入结果 —— 总行数/成功/失败与逐行错误明细。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceImportResultVO {

    /** CSV 数据总行数（不含表头） */
    private int total;

    /** 成功行数（含覆盖更新） */
    private int success;

    /** 失败行数 */
    private int failed;

    /** 失败明细（"第N行: 原因"） */
    private List<String> errors;
}
