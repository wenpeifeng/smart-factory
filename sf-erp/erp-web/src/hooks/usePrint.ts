import dayjs from 'dayjs'
import type { PrintBizType } from '@/api/print'

/**
 * 打印入口 Hook —— 各单据列表页直达 A4 打印预览的统一跳转。
 * <p>
 * 打印中心（/print）之外，销售订单/送货单/采购订单/月度对账单/记账凭证
 * 的列表页操作列均通过本 hook 唤起预览页（新窗口 1000×760，不脱离列表上下文）；
 * 预览页内部走 GET /report/print/{bizType}/{id}，纯只读无副作用，
 * 故入口按钮无需 Popconfirm 二次确认。
 *
 * @example
 * const { openPrint } = usePrint()
 * openPrint('sal_order', row.id)                    // 销售订单
 * openPrint('statement', row.id)                    // 客户月度对账单（默认当前月）
 * openPrint('statement', row.id, '2026-08')         // 指定对账月份
 */
export function usePrint() {
  /**
   * 在新窗口打开单据的 A4 打印预览（/print/preview/:bizType/:id）。
   *
   * @param bizType 打印业务类型（5 类模板之一，见 api/print.ts BIZ_TYPE_META）
   * @param id      单据 ID（statement 为客户 ID）
   * @param month   对账月份 yyyy-MM（仅 statement 用；缺省取当前月）
   */
  const openPrint = (bizType: PrintBizType, id: number, month?: string) => {
    const m = bizType === 'statement' ? (month ?? dayjs().format('YYYY-MM')) : undefined
    const query = m ? `?month=${m}` : ''
    window.open(`/print/preview/${bizType}/${id}${query}`, '_blank', 'width=1000,height=760')
  }

  return { openPrint }
}
