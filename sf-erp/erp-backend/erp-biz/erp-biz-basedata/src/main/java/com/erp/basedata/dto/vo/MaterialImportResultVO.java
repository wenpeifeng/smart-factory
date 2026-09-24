package com.erp.basedata.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 物料 CSV 导入结果 VO。
 * <p>导入策略：逐行校验、错误跳过（部分成功模式），错误明细行号从 2 起（1 为表头）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialImportResultVO {

    /** 导入总行数（不含表头） */
    private int total;

    /** 成功行数 */
    private int success;

    /** 失败行数 */
    private int fail;

    /** 错误明细（如：第3行: 物料名称不能为空） */
    private List<String> errors = new ArrayList<>();
}
