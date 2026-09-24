package com.erp.report.service;

import com.erp.report.dto.vo.DocChainVO;

/**
 * 单据联查服务（只读）：以任一业务单据为锚点，BFS 展开上下游链路。
 * <p>
 * 销售链：报价 → 订单 → 出库 → {退货 / 发票 / 应收} → 收款 → 凭证；<br>
 * 采购链：订单 → 入库 → {退货 / 发票 / 应付} → 付款 → 凭证；<br>
 * 红冲配对（biz_reverse_log）双向可达。
 */
public interface DocChainService {

    /**
     * 查询单据链路。
     *
     * @param bizType 单据类型（DocTypes 常量，如 SAL_ORDER）
     * @param bizId   单据 ID
     * @throws com.erp.common.exception.BusinessException 类型不支持或单据不存在
     */
    DocChainVO chain(String bizType, Long bizId);
}
