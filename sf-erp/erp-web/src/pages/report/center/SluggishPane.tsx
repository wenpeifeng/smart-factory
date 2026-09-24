import { useState } from 'react'
import { Alert, Button, Card, InputNumber, Space, Statistic, Table, Tag, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import type { ColumnsType } from 'antd/es/table'
import { downloadReportCsv, getSluggishMaterials, type SluggishRow } from '@/api/report'
import { fmtMoney, fmtQty, fmtRatio } from '@/pages/report/shared'

/**
 * 呆滞物料：现存量 > 0 且 N 天无出库流水的物料清单（库龄/金额/占比）。
 */
export default function SluggishPane() {
  const [days, setDays] = useState<number>(90)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['report', 'inventory-sluggish', days],
    queryFn: () => getSluggishMaterials(days),
  })

  const columns: ColumnsType<SluggishRow> = [
    { title: '物料编码', dataIndex: 'materialCode', key: 'materialCode', width: 130 },
    { title: '物料名称', dataIndex: 'materialName', key: 'materialName', ellipsis: true },
    { title: '单位', dataIndex: 'unitName', key: 'unitName', width: 70 },
    { title: '现存量', dataIndex: 'qty', key: 'qty', align: 'right', width: 100, render: (v: number) => fmtQty(v) },
    { title: '加权价', dataIndex: 'price', key: 'price', align: 'right', width: 100, render: (v: number) => v.toFixed(4) },
    { title: '呆滞金额(元)', dataIndex: 'value', key: 'value', align: 'right', width: 130, render: (v: number) => fmtMoney(v) },
    { title: '金额占比', dataIndex: 'ratio', key: 'ratio', align: 'right', width: 100, render: (v: number) => fmtRatio(v) },
    {
      title: '无出库天数',
      dataIndex: 'daysSinceOut',
      key: 'daysSinceOut',
      align: 'right',
      width: 110,
      render: (v: number | null) => (v == null ? <Tag color="red">从未出库</Tag> : v),
    },
    { title: '最后出库', dataIndex: 'lastOutTime', key: 'lastOutTime', width: 110 },
    {
      title: '库龄天数',
      dataIndex: 'ageDays',
      key: 'ageDays',
      align: 'right',
      width: 100,
      render: (v: number | null) => (v == null ? '-' : <Tag color={v > 180 ? 'red' : v > 90 ? 'orange' : 'green'}>{v}</Tag>),
    },
  ]

  const rows = data?.rows ?? []

  return (
    <Card
      title={`呆滞物料分析（${data?.days ?? days} 天无出库）`}
      extra={
        <Space>
          <InputNumber min={1} max={3650} value={days} onChange={(v) => setDays(v ?? 90)} addonAfter="天" style={{ width: 140 }} />
          <Button
            icon={<DownloadOutlined />}
            onClick={() => downloadReportCsv('/report/inventory/sluggish', { days }, `inventory_sluggish_${days}d.csv`)}
          >
            导出 CSV
          </Button>
        </Space>
      }
    >
      {isError && (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} message={`查询失败：${(error as Error)?.message ?? ''}`} />
      )}

      <Space size="large" style={{ marginBottom: 16 }}>
        <Statistic title="呆滞物料数" value={rows.length} valueStyle={{ fontWeight: 700 }} />
        <Statistic
          title="呆滞金额合计(元)"
          value={fmtMoney(data?.totalValue ?? 0)}
          valueStyle={{ fontWeight: 700, color: '#fa8c16' }}
        />
      </Space>

      <Table<SluggishRow>
        rowKey="materialId"
        size="middle"
        loading={isLoading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 20, showSizeChanger: false, showTotal: (t) => `共 ${t} 行` }}
      />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        口径：现存量 &gt; 0 且最近 {days} 天无出库方向流水（无任何出库流水视为呆滞，标「从未出库」）；
        库龄锚点 = 最后一次入库流水时间；金额 = 现存量 × 加权价（无采购记录回退 ref_cost）；占比按呆滞金额合计为分母。
      </Typography.Text>
    </Card>
  )
}
