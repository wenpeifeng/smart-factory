import { get, type PageResult } from '@/api/request'

/**
 * 单据打印中心模块 API（打印中心列表 + 5 类单据打印数据）。
 * 类型与后端 com.erp.report.dto.vo.PrintDataVO / PrintDocRowVO 一一对应；
 * 后端已把所有值格式化为展示字符串（金额千分位、数量去尾零），前端零格式化。
 */

// ==================== 业务类型元数据 ====================

/** 打印业务类型 */
export type PrintBizType = 'sal_order' | 'delivery_note' | 'pur_order' | 'statement' | 'voucher'

/** 打印业务类型元数据（Tab 标签 / 往来单位列名 / 单据标题） */
export const BIZ_TYPE_META: Record<
  PrintBizType,
  { label: string; title: string; partyLabel: string; dateLabel: string; amountLabel: string }
> = {
  sal_order: { label: '销售订单', title: '销售订单', partyLabel: '客户', dateLabel: '订单日期', amountLabel: '订单金额' },
  delivery_note: { label: '送货单', title: '销售出库单（送货单）', partyLabel: '客户', dateLabel: '出库日期', amountLabel: '出库金额' },
  pur_order: { label: '采购订单', title: '采购订单', partyLabel: '供应商', dateLabel: '订单日期', amountLabel: '订单金额' },
  statement: { label: '月度对账单', title: '客户月度对账单', partyLabel: '客户名称', dateLabel: '对账月份', amountLabel: '当月应收' },
  voucher: { label: '记账凭证', title: '记账凭证', partyLabel: '制单人', dateLabel: '凭证日期', amountLabel: '借方合计' },
}

// ==================== 打印数据 VO ====================

/** 主信息区/合计区字段（标签-值） */
export interface PrintField {
  label: string
  value: string | null
}

/** 明细表列定义 */
export interface PrintColumn {
  key: string
  label: string
  /** 列宽（百分比数字，列宽之和建议为 100；空则均分） */
  width?: number | null
  /** 对齐：left/center/right */
  align?: string | null
}

/** 标准打印数据（PrintPage A4 模板直接渲染） */
export interface PrintData {
  bizType: PrintBizType
  company: string
  title: string
  billNo: string
  billDate: string
  headerFields: PrintField[] | null
  columns: PrintColumn[] | null
  details: Record<string, string | null>[] | null
  totals: PrintField[] | null
  amountInWords: string | null
  attachmentNote: string | null
  signatures: string[] | null
  remark: string | null
  totalAmount: number | null
}

/** 单据选择列表行（编号/往来单位/日期/金额/状态） */
export interface PrintDocRow {
  id: number
  billNo: string
  partyName: string | null
  billDate: string
  amount: number
  statusLabel: string | null
  extra: string | null
}

// ==================== 接口 ====================

/**
 * 打印单据选择列表（分页）。
 *
 * @param bizType 业务类型
 * @param params  pageNum/pageSize/keyword（单号·往来单位）；statement 额外传 month=yyyy-MM
 */
export const getPrintDocs = (bizType: PrintBizType, params: Record<string, unknown>) =>
  get<PageResult<PrintDocRow>>(`/report/print/list/${bizType}`, params)

/**
 * 打印数据（标准打印 VO）。
 *
 * @param bizType 业务类型
 * @param id      单据 ID（statement 为客户 ID）
 * @param month   对账月份 yyyy-MM（仅 statement）
 */
export const getPrintData = (bizType: PrintBizType, id: number, month?: string) =>
  get<PrintData>(`/report/print/${bizType}/${id}`, month ? { month } : undefined)
