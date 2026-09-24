package com.erp.report.service;

import com.erp.common.result.PageResult;
import com.erp.report.dto.vo.PrintDataVO;
import com.erp.report.dto.vo.PrintDocRowVO;

/**
 * 单据打印中心服务：5 类单据模板的打印数据装配。
 * <p>
 * bizType：sal_order（销售订单）/ delivery_note（销售出库·送货单）/
 * pur_order（采购订单）/ statement（客户月度对账单）/ voucher（记账凭证）。
 */
public interface PrintService {

    /**
     * 打印单据选择列表（分页）。
     *
     * @param bizType  业务类型（同上）
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 页大小
     * @param keyword  单号/往来单位模糊过滤（可空）
     * @param month    对账月份 yyyy-MM（仅 statement 使用，其他类型忽略）
     * @return 统一列表行（编号/往来单位/日期/金额/状态）
     * @throws com.erp.common.exception.BusinessException bizType 非法或 month 格式错误
     */
    PageResult<PrintDocRowVO> listDocs(String bizType, int pageNum, int pageSize, String keyword, String month);

    /**
     * 装配标准打印 VO（前端 A4 模板直接渲染）。
     *
     * @param bizType 业务类型（同上）
     * @param id      单据 ID（statement 为客户 ID）
     * @param month   对账月份 yyyy-MM（仅 statement 使用，空默认当月）
     * @return 标准打印数据（含主信息区/明细/合计/大写金额/签字栏）
     * @throws com.erp.common.exception.BusinessException bizType 非法、month 格式错误或单据不存在
     */
    PrintDataVO buildPrintData(String bizType, Long id, String month);
}
