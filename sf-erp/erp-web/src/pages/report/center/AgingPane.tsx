import { Alert, Button, Card, Col, Progress, Row, Statistic, Table } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import type { ColumnsType } from 'antd/es/table'
import { downloadReportCsv, getInventoryAging, type AgingDetail } from '@/api/report'
import { fmtMoney, fmtQty, fmtRatio } from '@/pages/report/shared'

/** 分桶颜色（与 AntD 语义色对应：新库存绿 → 长库龄红） */
const BUCKET_COLORS: Record<string, string> = {
  '0-30': 'green',
  '31-90': 'blue',
  '91-180': 'orange',
  '180+': 'red',
  NONE: 'default',
}

/**
 * 库存库龄分析：分桶汇总卡片（0-30/31-90/91-180/180+ 天 + 占比）+ 明细表 + CSV 导出。
 * 库龄锚点=仓+物料最后一次入库方向流水时间（inv_movement.qty>0 的 MAX(create_time)）。
 */
export default function AgingPane() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['report', 'inventory-aging'],
    queryFn: getInventoryAging,
  })

  const buckets = data?.buckets ?? []
  const details = data?.details ?? []

  const columns: ColumnsType<AgingDetail> = [
    { title: '仓库', dataIndex: 'warehouseName', key: 'warehouseName', width: 110, render: (v: string | null, r) => v ?? `仓库#${r.warehouseId}` },
    { title: '物料编码', dataIndex: 'materialCode', key: 'materialCode', width: 130 },
    { title: '物料名称', dataIndex: 'materialName', key: 'materialName', ellipsis: true },
    { title: '现存量', dataIndex: 'qty', key: 'qty', align: 'right', width: 110, render: (v: number) => fmtQty(v) },
    { title: '加权价(元)', dataIndex: 'price', key: 'price', align: 'right', width: 110, render: (v: number) => fmtMoney(v) },
    { title: '库存价值(元)', dataIndex: 'value', key: 'value', align: 'right', width: 130, render: (v: number) => fmtMoney(v) },
    { title: '最后入库时间', dataIndex: 'lastInTime', key: 'lastInTime', width: 150, render: (v: string | null) => v ?? '-' },
    {
      title: '库龄天数',
      dataIndex: 'ageDays',
      key: 'ageDays',
      align: 'right',
      width: 100,
      sorter: (a, b) => (a.ageDays ?? 1e9) - (b.ageDays ?? 1e9),
      render: (v: number | null) => (v == null ? '-' : `${v} 天`),
    },
    {
      title: '库龄分桶',
      dataIndex: 'bucket',
      key: 'bucket',
      width: 110,
      filters: buckets.map((b) => ({ text: b.label, value: b.bucket })),
      onFilter: (value, record) => record.bucket === value,
      render: (v: string, r) => ({ children: <span style={{ color: BUCKET_COLORS[v] === 'red' ? '#ff4d4f' : undefined }}>{r.bucketLabel}</span> }),
    },
  ]

  return (
    <Card
      title="库存库龄分析（按最后一次入库时间分桶）"
      extra={
        <Button icon={<DownloadOutlined />} onClick={() => downloadReportCsv('/report/inventory/aging', {}, 'inventory_aging.csv')}>
          导出 CSV
        </Button>
      }
    >
      {isError && (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} message={`查询失败：${(error as Error)?.message ?? ''}`} />
      )}

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        {buckets.map((b, i) => (
          <Col xs={12} sm={8} lg={Math.floor(24 / Math.max(buckets.length, 1))} key={b.bucket}>
            <Card size="small" loading={isLoading}>
              <Statistic
                title={b.label}
                value={fmtQty(b.qty)}
                suffix={
                  <span style={{ fontSize: 12, color: '#8c939c', marginLeft: 8 }}>
                    占比 {fmtRatio(b.ratio)}
                  </span>
                }
                valueStyle={{ color: ['#52c41a', '#1668dc', '#fa8c16', '#ff4d4f', '#5b6b7c'][i] ?? undefined }}
              />
              <Progress
                percent={Math.round((b.ratio ?? 0) * 100)}
                showInfo={false}
                size="small"
                strokeColor={(() => {
                  switch (b.bucket) {
                    case '0-30':
                      return '#52c41a'
                    case '31-90':
                      return '#1668dc'
                    case '91-180':
                      return '#fa8c16'
                    case '180+':
                      return '#ff4d4f'
                    default:
                      return '#8c939c'
                  }
                })()}
              />
            </Card>
          </Col>
        ))}
      </Row>

      <Table<AgingDetail>
        rowKey={(r) => `${r.warehouseId}-${r.materialId}`}
        size="middle"
        loading={isLoading}
        columns={columns}
        dataSource={details}
        pagination={{ pageSize: 20, showSizeChanger: false, showTotal: (t) => `共 ${t} 行` }}
      />
    </Card>
  )
}
