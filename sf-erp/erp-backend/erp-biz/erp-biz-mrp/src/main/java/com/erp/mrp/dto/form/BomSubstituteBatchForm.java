package com.erp.mrp.dto.form;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 按子件行批量保存替代料表单（V621，BOM 维护界面整组提交）。
 */
@Data
public class BomSubstituteBatchForm implements Serializable {

    /** 替代料明细（整组重建：为空 = 清空该子件全部替代料） */
    private List<BomSubstituteForm> items;
}
