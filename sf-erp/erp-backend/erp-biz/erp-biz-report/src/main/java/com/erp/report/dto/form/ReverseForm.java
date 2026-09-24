package com.erp.report.dto.form;

import lombok.Data;

/**
 * 通用红冲入参（红冲原因必填，落 biz_reverse_log 留痕）。
 */
@Data
public class ReverseForm {

    /** 红冲原因（审计留痕） */
    private String reason;
}
