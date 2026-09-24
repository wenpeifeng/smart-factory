package com.erp.sales.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 销售发货通知单详情聚合 VO：表头 + 明细行。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalNoticeDetailBundle {

    /** 表头 */
    private SalNoticeVO notice;

    /** 明细行 */
    private List<SalNoticeItemVO> items;
}
