package com.erp.purchase.service;

import com.erp.purchase.dto.form.EstimationReverseForm;
import com.erp.purchase.dto.query.PurEstimationQuery;
import com.erp.purchase.dto.vo.PurEstimationSummaryVO;
import com.erp.purchase.dto.vo.PurEstimationVO;
import com.erp.common.result.PageResult;

/**
 * 采购暂估应付（货到票未到）Service——V231。
 * <p>
 * 口径（对标金蝶暂估入库）：
 * <ul>
 *   <li>暂估金额 = Σ 入库明细数量 × 含税单价 ÷ (1+税率%)（不含税净额，HALF_UP 2 位）；</li>
 *   <li>生成：入库审核（instant 模式事件/扫描器兜底，或 month-end 模式月初补提）
 *       且无生效蓝字进项发票时，写 pur_estimation + fin_payable（biz_no=ZG 单号）；</li>
 *   <li>冲回：进项发票认证后自动（扫描器）或手动，写 fin_payable 红字行（-冲回额）+
 *       正式应付行（+发票实际额）+ 差额调整行（实际≠冲回时）；</li>
 *   <li>fin_payable 为 finance 模块约定表：本模块只 INSERT，不做 UPDATE/DELETE；
 *       biz_no 前缀 ZG 的行即暂估链路产生的暂估/红冲/差额行。</li>
 * </ul>
 */
public interface PurEstimationService {

    /** 分页查询暂估单 */
    PageResult<PurEstimationVO> page(PurEstimationQuery query);

    /** 暂估报表（按期间 yyyyMM 汇总 + 供应商维度） */
    PurEstimationSummaryVO summary(String month);

    /** 手工生成暂估：POST /purchase/estimations/from-instock/{instockId} */
    Long createFromInstock(Long instockId);

    /** 手动冲回：POST /purchase/estimations/{id}/reverse */
    void reverse(Long id, EstimationReverseForm form);

    /** 扫描器一轮：补提漏单暂估 + 已认证发票自动冲回（吞异常，逐单独立事务） */
    void scanOnce();
}
