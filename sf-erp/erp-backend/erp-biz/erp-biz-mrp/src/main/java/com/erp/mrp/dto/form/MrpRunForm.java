package com.erp.mrp.dto.form;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * MRP 运算触发表单：mpsIds 为空 = 运算全部“待运算”计划行。
 */
@Data
public class MrpRunForm implements Serializable {

    /** 指定运算的 MPS 行ID（可空=全部待运算） */
    private List<Long> mpsIds;

    /** 运算备注 */
    private String remark;
}
