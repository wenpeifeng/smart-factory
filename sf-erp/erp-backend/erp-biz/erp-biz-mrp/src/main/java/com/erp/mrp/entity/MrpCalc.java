package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * MRP 运算批次 —— 一次 calculate 调用生成一行，batch_no 全模块追溯键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_calc")
public class MrpCalc extends BaseEntity implements Serializable {

    /** 运算批次号（MR+yyyyMMdd+4位流水） */
    private String batchNo;

    /** 本次运算 MPS 行数 */
    private Integer mpsCount;

    /** 本次产出的发补建议条数 */
    private Integer suggestionCount;

    /** 运算备注 */
    private String remark;
}
