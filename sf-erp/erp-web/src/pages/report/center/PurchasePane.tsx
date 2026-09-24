import { useMemo, useState } from 'react'
import { Alert, Button, Card, Col, DatePicker, Row, Select, Space, Table, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import { downloadReportCsv, getPurchaseDetail, getSupplierOptions, type ReportRow } from '@/api/report'
import { fmtMoney, fmtQty } from '@/pages/report/shared'

/** 聚合维度选项（采购） */
const GROUP_OPTIONS = [
  { value: 'material', label: '按物料' },
  { value: 'supplier', label: '按供应商' },
  { value: 'month', label: '按月' },
]

type GroupBy = 'material' | 'supplier' | 'month'

/** 默认查询区间：当月 1 日 ~ 今天 */
function defaultRange(): [Dayjs, Dayjs] {
  const today = dayjs()
  return [today.startOf('month'), today]
}

/**
 * 采购明细报表：筛选栏（日期区间/供应商/聚合维度）+ 聚合表 + CSV 导出。
 * 口径：仅已审核采购入库；金额为含税价税合计。
 */
export default function PurchasePane() {
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null]>(defaultRange())
  const [supplierId, setSupplierId] = useState<number | undefined>(undefined)
  const [groupBy, setGroupBy] = useState<GroupBy>('material')

  // 后端 to 为开区间（不含当日）→ 传 end+1 天
  const params = useMemo(
    () => ({
      from: range[0]?.format('YYYY-MM-DD'),
      to: range[1]?.add(1, 'day').format('YYYY-MM-DD'),
      supplierId,
      groupBy,
    }),
    [range, supplierId, groupBy],
  )

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['report', 'purchase-detail', params],
    queryFn: () => getPurchaseDetail(params),
  })

  const { data: suppliers } = useQuery({
    queryKey: ['report', 'options', 'suppliers'],
    queryFn: getSupplierOptions,
  })

  const columns: ColumnsType<ReportRow> = useMemo(() => {
    const dimCols: ColumnsType<ReportRow> =
      groupBy === 'month'
        ? [{ title: '月份', dataIndex: 'dimCode', key: 'dimCode', width: 120 }]
        : [
            { title: groupBy === 'supplier' ? '供应商编码' : '物料编码', dataIndex: 'dimCode', key: 'dimCode', width: 140 },
            { title: groupBy === 'supplier' ? '供应商名称' : '物料名称', dataIndex: 'dimName', key: 'dimName', ellipsis: true },
          ]
    return [
      ...dimCols,
      { title: '数量', dataIndex: 'qty', key: 'qty', align: 'right', width: 110, render: (v: number) => fmtQty(v) },
      { title: '价税合计(元)', dataIndex: 'amount', key: 'amount', align: 'right', width: 140, render: (v: number) => fmtMoney(v) },
    ]
  }, [groupBy])

  const rows = data ?? []
  const total = (pick: (r: ReportRow) => number | null) => rows.reduce((acc, r) => acc + (pick(r) ?? 0), 0)

  return (
    <Card title="采购明细（已审核入库，含税价税合计）">
      <Space wrap style={{ marginBottom: 16 }}>
        <DatePicker.RangePicker value={range} onChange={(v) => setRange(v ?? [null, null])} allowEmpty={[true, true]} />
        <Select
          style={{ width: 220 }}
          placeholder="全部供应商"
          allowClear
          showSearch
          optionFilterProp="label"
          value={supplierId}
          onChange={setSupplierId}
          options={(suppliers ?? []).map((s) => ({ value: s.id, label: `${s.code} ${s.name}` }))}
        />
        <Select style={{ width: 120 }} value={groupBy} onChange={(v) => setGroupBy(v as GroupBy)} options={GROUP_OPTIONS} />
        <Button
          icon={<DownloadOutlined />}
          onClick={() => downloadReportCsv('/report/purchase/detail', params, `purchase_detail_${groupBy}.csv`)}
        >
          导出 CSV
        </Button>
      </Space>

      {isError && (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} message={`查询失败：${(error as Error)?.message ?? ''}`} />
      )}

      <Table<ReportRow>
        rowKey="dimCode"
        size="middle"
        loading={isLoading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 20, showSizeChanger: false, showTotal: (t) => `共 ${t} 行` }}
        summary={() =>
          rows.length === 0 ? null : (
            <Table.Summary fixed>
              <Table.Summary.Row>
                <Table.Summary.Cell index={0} colSpan={groupBy === 'month' ? 1 : 2}>
                  <strong>合计</strong>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={1} align="right">
                  <strong>{fmtQty(total((r) => r.qty))}</strong>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={2} align="right">
                  <strong>{fmtMoney(total((r) => r.amount))}</strong>
                </Table.Summary.Cell>
              </Table.Summary.Row>
            </Table.Summary>
          )
        }
      />
      <Row style={{ marginTop: 4 }}>
        <Col span={24}>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            口径：仅统计「已审核」采购入库单；金额=明细价税合计（含税）。
          </Typography.Text>
        </Col>
      </Row>
    </Card>
  )
}
