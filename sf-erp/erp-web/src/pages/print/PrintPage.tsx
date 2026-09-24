import { Fragment } from 'react'
import dayjs from 'dayjs'
import { useAuthStore } from '@/stores/auth'
import type { PrintColumn, PrintData } from '@/api/print'
import './printDoc.css'

/**
 * 统一 A4 单据版式组件（打印中心 5 类模板的公共底座）。
 * <p>
 * 结构：公司抬头 → 单据标题 → 单号/日期（+附单据张数）→ 主信息区（两列网格）
 * → 明细表（列定义/对齐由后端 PrintColumn 驱动）→ 合计区 + 大写金额
 * → 签字栏 → 页脚（打印时间/打印人）。
 * 纯展示、零请求、无动效（打印物本身不加动画，规范要求）；
 * 屏显为 210×297mm 纸张，window.print() 时由 printDoc.css 的
 * @media print 规则只输出纸张内容。
 */

interface PrintPageProps {
  /** 后端标准打印数据 */
  data: PrintData
  /** 是否展示大写金额行（凭证样式不展示，默认 true） */
  showWords?: boolean
  /** 模板附加脚注（如送货单联次说明），可空 */
  footnote?: string
}

/** 列对齐 class（与 printDoc.css 中 .print-align-* 对应） */
function alignClass(col: PrintColumn): string {
  if (col.align === 'right') return 'print-align-right'
  if (col.align === 'center') return 'print-align-center'
  return ''
}

/** 明细表列宽（后端给百分比，缺省均分） */
function colWidth(col: PrintColumn, count: number): string {
  return col.width && col.width > 0 ? `${col.width}%` : `${Math.floor(100 / count)}%`
}

export default function PrintPage({ data, showWords = true, footnote }: PrintPageProps) {
  const user = useAuthStore((s) => s.user)
  const columns = data.columns ?? []
  const details = data.details ?? []
  const headerFields = data.headerFields ?? []

  // 主信息区两两一行（label + value 两个单元格 × 2 列）
  const fieldRows: { label: string; value: string }[][] = []
  for (let i = 0; i < headerFields.length; i += 2) {
    fieldRows.push([
      { label: headerFields[i].label ?? '', value: headerFields[i].value ?? '' },
      {
        label: headerFields[i + 1]?.label ?? '',
        value: headerFields[i + 1]?.value ?? '',
      },
    ])
  }

  return (
    <div className="print-sheet">
      <div className="print-company">{data.company}</div>
      <div className="print-title">{data.title}</div>
      <div className="print-subline">
        <span>
          单据编号：<strong>{data.billNo || '-'}</strong>
        </span>
        <span>
          单据日期：<strong>{data.billDate || '-'}</strong>
        </span>
        {data.attachmentNote && <span>{data.attachmentNote}</span>}
      </div>

      {fieldRows.length > 0 && (
        <table className="print-fields">
          <tbody>
            {fieldRows.map((row, idx) => (
              <tr key={idx}>
                {row.map((f, i) => (
                  <Fragment key={`${idx}-${i}`}>
                    <td className="print-field-label">{f.label || ''}</td>
                    <td>{f.value || '　'}</td>
                  </Fragment>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <table className="print-table">
        <colgroup>
          {columns.map((c) => (
            <col key={c.key} style={{ width: colWidth(c, columns.length) }} />
          ))}
        </colgroup>
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} className={alignClass(c)}>
                {c.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {details.length === 0 ? (
            <tr className="print-empty-row">
              <td colSpan={Math.max(columns.length, 1)}>（无明细数据）</td>
            </tr>
          ) : (
            details.map((row, idx) => (
              <tr key={idx}>
                {columns.map((c) => (
                  <td key={c.key} className={alignClass(c)}>
                    {row[c.key] ?? ''}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>

      {(data.totals?.length || (showWords && data.amountInWords)) && (
        <div className="print-totals">
          {data.totals && data.totals.length > 0 && (
            <div className="print-totals-row">
              {data.totals.map((t) => (
                <span className="print-total-item" key={t.label}>
                  {t.label}：<strong>{t.value ?? '-'}</strong>
                </span>
              ))}
            </div>
          )}
          {showWords && data.amountInWords && (
            <div className="print-words">金额大写：{data.amountInWords}</div>
          )}
        </div>
      )}

      {data.remark && <div className="print-remark">备注：{data.remark}</div>}

      <div className="print-signatures">
        {(data.signatures ?? []).map((s) => (
          <span className="print-sign-item" key={s}>
            {s}
          </span>
        ))}
      </div>

      {footnote && <div className="print-footnote">{footnote}</div>}

      <div className="print-footer">
        <span>打印人：{user?.nickname ?? user?.username ?? '-'}</span>
        <span>打印时间：{dayjs().format('YYYY-MM-DD HH:mm:ss')}</span>
      </div>
    </div>
  )
}
