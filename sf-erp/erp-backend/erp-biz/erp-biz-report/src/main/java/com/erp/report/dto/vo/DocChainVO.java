package com.erp.report.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单据联查结果：以入参单据为锚点，上游链（按业务发生顺序在前）+ 下游链（在后）。
 * <p>
 * 销售方向：报价 → 订单 → 出库 → 发票/退货 → 应收 → 收款 → 凭证；<br>
 * 采购方向：订单 → 入库 → 发票/退货 → 应付 → 付款 → 凭证。
 * 链上每跳可能一分为多（一张订单多次出库/开票），BFS 展开，深度上限 8。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocChainVO {

    /** 锚点单据（入参 bizType/bizId 解析出的节点） */
    private DocChainNodeVO anchor;

    /** 上游节点（从锚点向业务源头追溯，按远近排序） */
    private List<DocChainNodeVO> upstream;

    /** 下游节点（从锚点向凭证/资金端展开，按远近排序） */
    private List<DocChainNodeVO> downstream;
}
