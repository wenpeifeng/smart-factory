import type { ComponentType } from 'react'
import PrintPage from './PrintPage'
import type { PrintBizType, PrintData } from '@/api/print'

/**
 * 5 类单据打印模板 —— 全部基于统一 A4 版式组件 PrintPage 实现。
 * <p>
 * 模板只做「版式差异」：脚注联次说明、大写金额是否展示等；
 * 数据结构（主信息区/明细列/合计/签字栏）由后端标准打印 VO 驱动。
 * 新增模板三步法见 docs/modules/print.md。
 */

/** 销售订单：标准单据版式 */
export function SalOrderTemplate({ data }: { data: PrintData }) {
  return <PrintPage data={data} />
}

/** 送货单：含客户签收栏 + 一式三联说明 */
export function DeliveryNoteTemplate({ data }: { data: PrintData }) {
  return (
    <PrintPage
      data={data}
      footnote="本送货单一式三联：白联·客户联 / 红联·回单联 / 黄联·存根联；货物经客户签收后作为收货与结算凭证。"
    />
  )
}

/** 采购订单：标准单据版式 + 生效说明 */
export function PurOrderTemplate({ data }: { data: PrintData }) {
  return <PrintPage data={data} footnote="本订单经双方签字盖章后生效；价税合计金额包含增值税。" />
}

/** 月度对账单：期初/本期/期末应收 + 客户确认栏 */
export function StatementTemplate({ data }: { data: PrintData }) {
  return (
    <PrintPage
      data={data}
      footnote="如对以上账目有异议，请于收到本对账单后 7 个工作日内书面提出，逾期视为确认。"
    />
  )
}

/** 记账凭证（金蝶样式）：摘要/科目/借贷方，附单据张数，不印大写金额 */
export function VoucherTemplate({ data }: { data: PrintData }) {
  return <PrintPage data={data} showWords={false} />
}

/** 模板注册表：bizType → 模板组件（PrintPreview 按 bizType 取用） */
export const PRINT_TEMPLATES: Record<PrintBizType, ComponentType<{ data: PrintData }>> = {
  sal_order: SalOrderTemplate,
  delivery_note: DeliveryNoteTemplate,
  pur_order: PurOrderTemplate,
  statement: StatementTemplate,
  voucher: VoucherTemplate,
}
