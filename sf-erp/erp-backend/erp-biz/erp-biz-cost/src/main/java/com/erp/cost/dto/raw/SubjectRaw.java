package com.erp.cost.dto.raw;

import lombok.Data;

/**
 * fin_subject 只读投影（结账凭证科目解析）。
 */
@Data
public class SubjectRaw {

    /** 科目 ID */
    private Long id;

    /** 科目编码 */
    private String code;

    /** 科目名称 */
    private String name;
}
