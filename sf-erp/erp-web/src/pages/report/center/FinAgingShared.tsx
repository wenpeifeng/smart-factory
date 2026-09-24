import { Table, Tooltip } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { fmtMoney, fmtRatio } from '@/pages/report/shared'
import type { PartnerAging, PartnerAgingRow } from '@/api/finance'

/**
 * 应收/应付账龄共用渲染：分桶汇总条 + 客商×账龄明细表（分桶色阶）。
 * 色阶口径：单元格背景 = 桶基色 × 金额占该桶最大值的强度（金额越大越深），
 * 桶越老基色越红（1-30 绿 → 180+ 深红），一眼定位高风险客商。
 */

/** 分桶定义：字段名 / 标题 / 基色（rgba 前三段） */
const BUCKETS: { key: keyof PartnerAgingRow; title: string; color: [number, number, number] }[] = [
  { key: 'd30', title: '1-30天', color: [82, 196, 26] },
  { key: 'd60', title: '31-60天', color: [19, 194, 194] },
  { key: 'd90', title: '61-90天', color: [250, 173, 20] },
  { key: 'd180', title: '91-180天', color: [250, 140, 22] },
  { key: 'd180p', title: '180天以上', color: [255, 77, 79] },
]

/** 桶单元格背景色（value / max 决定强度，空桶不着色） */
function bucketBackground(color: [number, number, number], value: number, max: number): string | undefined {
  if (!value || !max) return undefined
  const intensity = Math.min(0.08 + (value / max) * 0.52, 0.6)
  return `rgba(${color[0]}, ${color[1]}, ${color[2]}, ${intensity.toFixed(3)})`
}

/** 分桶汇总条（每桶金额 + 占比 + 基色色块） */
export function AgingBucketStrip({ data }: { data: PartnerAging }) {
  if (!data.buckets?.length) return null
  return (
    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 12 }}>
      {data.buckets.map((b, i) => (
        <div
          key={b.key}
          style={{
            flex: `1 1 140px`,
            minWidth: 140,
            border: '1px solid #f0f0f0',
            borderRadius: 8,
            padding: '8px 12px',
            background: `linear-gradient(135deg, rgba(${BUCKETS[i].color.join(',')},0.10), #fff)`,
          }}
        >
          <div style={{ fontSize: 12, color: '#8c939c' }}>
            <span
              style={{
                display: 'inline-block',
                width: 8,
                height: 8,
                borderRadius: 4,
                background: `rgb(${BUCKETS[i].color.join(',')})`,
                marginRight: 6,
              }}
            />
            {b.label}
          </div>
          <div style={{ fontSize: 16, fontWeight: 700, marginTop: 2 }}>{fmtMoney(b.amount)}</div>
          <div style={{ fontSize: 12, color: '#8c939c' }}>占比 {fmtRatio(b.ratio)}</div>
        </div>
      ))}
    </div>
  )
}

/** 客商 × 账龄明细表（含合计行、分桶色阶、占比列） */
export function AgingPartnerTable({ data, partnerTitle }: { data: PartnerAging; partnerTitle: string }) {
  const rows: PartnerAgingRow[] = data.rows ?? []
  const bucketMax = BUCKETS.map((b) => Math.max(...rows.map((r) => Number(r[b.key] ?? 0)), 0))

  const columns: ColumnsType<PartnerAgingRow> = [
    {
      title: partnerTitle,
      dataIndex: 'partnerName',
      render: (_: unknown, r: PartnerAgingRow) => r.partnerName ?? `#${r.partnerId}`,
      ellipsis: true,
    },
    { title: '单据数', dataIndex: 'docCount', width: 80, align: 'right' },
    ...BUCKETS.map((b, i) => ({
      title: b.title,
      dataIndex: b.key as string,
      align: 'right' as const,
      width: 110,
      render: (v: number) => (
        <Tooltip title={`${b.title} ${fmtMoney(v)}`}>
          <span
            style={{
              display: 'inline-block',
              minWidth: '88%',
              textAlign: 'right',
              padding: '2px 6px',
              borderRadius: 4,
              background: bucketBackground(b.color, v ?? 0, bucketMax[i]),
              fontWeight: v > 0 ? 600 : 400,
            }}
          >
            {fmtMoney(v)}
          </span>
        </Tooltip>
      ),
    })),
    {
      title: '未结算合计',
      dataIndex: 'total',
      align: 'right',
      width: 120,
      render: (v: number) => <b>{fmtMoney(v)}</b>,
    },
    {
      title: '占比',
      dataIndex: 'ratio',
      align: 'right',
      width: 90,
      render: (v: number) => fmtRatio(v),
    },
  ]

  return (
    <Table<PartnerAgingRow>
      size="small"
      rowKey={(r) => String(r.partnerId)}
      columns={columns}
      dataSource={rows}
      pagination={rows.length > 15 ? { pageSize: 15, showSizeChanger: false } : false}
      summary={() => {
        const t = data.totalRow
        if (!t) return null
        return (
          <Table.Summary.Row style={{ background: '#fafafa', fontWeight: 700 }}>
            <Table.Summary.Cell index={0}>合计</Table.Summary.Cell>
            <Table.Summary.Cell index={1} align="right">
              {t.docCount}
            </Table.Summary.Cell>
            {BUCKETS.map((b, i) => (
              <Table.Summary.Cell key={b.key} index={i + 2} align="right">
                {fmtMoney(Number(t[b.key] ?? 0))}
              </Table.Summary.Cell>
            ))}
            <Table.Summary.Cell index={7} align="right">
              {fmtMoney(t.total)}
            </Table.Summary.Cell>
            <Table.Summary.Cell index={8} align="right">
              100.00%
            </Table.Summary.Cell>
          </Table.Summary.Row>
        )
      }}
    />
  )
}

/** 分桶守恒断言文案（分桶合计 = 未结算总计） */
export function agingBucketSumText(data: PartnerAging): string {
  const bucketSum = (data.buckets ?? []).reduce((acc, b) => acc + (b.amount ?? 0), 0)
  return `分桶合计 ${fmtMoney(bucketSum)} = 未结算总计 ${fmtMoney(data.totalAmount)}`
}
