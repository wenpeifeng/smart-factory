import { useMemo, useState } from 'react'
import { Alert, Button, Card, DatePicker, Space, Table, Tag, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import { downloadReportCsv, getSupplierPerformance, type SupplierPerformanceRow } from '@/api/report'
import { fmtMoney, fmtQty } from '@/pages/report/shared'

/** 默认查询区间：当年 1 月 1 日 ~ 今天 */
function defaultRange(): [Dayjs, Dayjs] {
  const today = dayjs()
  return [today.startOf('year'), today]
}

/** N/A 渲染：null = 无数据 */
function naPct(v: number | null): React.ReactNode {
  if (v == null) return <Tag>N/A</Tag>
  const color = v >= 95 ? 'green' : v >= 80 ? 'orange' : 'red'
  return <Tag color={color}>{v.toFixed(2)}%</Tag>
}

/**
 * 供应商绩效：采购额 / 订单数 / 到货及时率 / IQC 质检合格率（无质检数据 N/A）。
 */
export default function SupplierPerformancePane() {
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null]>(defaultRange())

  const params = useMemo(
    () => ({
      from: range[0]?.format('YYYY-MM-DD'),
      to: range[1]?.add(1, 'day').format('YYYY-MM-DD'),
    }),
    [range],
  )

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['report', 'supplier-performance', params],
    queryFn: () => getSupplierPerformance(params),
  })

  const columns: ColumnsType<SupplierPerformanceRow> = [
    { title: '供应商编码', dataIndex: 'supplierCode', key: 'supplierCode', width: 130 },
    { title: '供应商名称', dataIndex: 'supplierName', key: 'supplierName', ellipsis: true },
    { title: '订单数', dataIndex: 'orderCount', key: 'orderCount', align: 'right', width: 90 },
    { title: '订单金额(元)', dataIndex: 'orderAmount', key: 'orderAmount', align: 'right', width: 130, render: (v: number) => fmtMoney(v) },
    { title: '入库金额(元)', dataIndex: 'instockAmount', key: 'instockAmount', align: 'right', width: 130, render: (v: number) => fmtMoney(v) },
    { title: '到货及时率', dataIndex: 'onTimeRate', key: 'onTimeRate', align: 'center', width: 110, render: naPct },
    {
      title: '质检合格率',
      dataIndex: 'qualifiedRate',
      key: 'qualifiedRate',
      align: 'center',
      width: 110,
      render: (v: number | null, record: SupplierPerformanceRow) =>
        v == null ? <Tag>N/A</Tag> : (
          <span>
            <Tag color={v >= 95 ? 'green' : v >= 80 ? 'orange' : 'red'}>{v.toFixed(2)}%</Tag>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              ({fmtQty(record.qualifiedQty ?? 0)}/{fmtQty(record.sampleQty ?? 0)})
            </Typography.Text>
          </span>
        ),
    },
  ]

  const rows = data?.rows ?? []

  return (
    <Card
      title="供应商绩效分析"
      extra={
        <Button
          icon={<DownloadOutlined />}
          onClick={() => downloadReportCsv('/report/purchase/supplier-performance', params, 'supplier_performance_all.csv')}
        >
          导出 CSV
        </Button>
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <DatePicker.RangePicker value={range} onChange={(v) => setRange(v ?? [null, null])} allowEmpty={[true, true]} />
      </Space>

      {isError && (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} message={`查询失败：${(error as Error)?.message ?? ''}`} />
      )}

      <Table<SupplierPerformanceRow>
        rowKey="supplierId"
        size="middle"
        loading={isLoading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 20, showSizeChanger: false, showTotal: (t) => `共 ${t} 个供应商` }}
      />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        口径：订单数/金额 = 采购订单（已审核/部分入库/完成入库）；入库金额 = 已审核采购入库价税合计；
        到货及时率 = 入库日期 ≤ 计划交货日期（订单行 delivery_date，空回退订单日期）的入库明细行占比；
        质检合格率 = IQC（来源=采购入库）合格数/抽检数，无质检数据展示 N/A。
      </Typography.Text>
    </Card>
  )
}
