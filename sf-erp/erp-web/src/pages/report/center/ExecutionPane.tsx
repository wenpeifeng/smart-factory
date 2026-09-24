import { useMemo, useState } from 'react'
import { Alert, Button, Card, DatePicker, Progress, Space, Table, Tag, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import { downloadReportCsv, getWorkExecution, type WorkExecutionRow } from '@/api/report'
import { fmtQty } from '@/pages/report/shared'

/** 默认查询区间：近 180 天 */
function defaultRange(): [Dayjs, Dayjs] {
  const today = dayjs()
  return [today.subtract(180, 'day'), today]
}

/**
 * 生产订单执行跟踪：工单 计划量/完工量/在制/完工率/超期标记。
 */
export default function ExecutionPane() {
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null]>(defaultRange())

  const params = useMemo(
    () => ({
      from: range[0]?.format('YYYY-MM-DD'),
      to: range[1]?.add(1, 'day').format('YYYY-MM-DD'),
    }),
    [range],
  )

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['report', 'wo-execution', params],
    queryFn: () => getWorkExecution(params),
  })

  const columns: ColumnsType<WorkExecutionRow> = [
    { title: '工单号', dataIndex: 'orderNo', key: 'orderNo', width: 150 },
    { title: '物料编码', dataIndex: 'materialCode', key: 'materialCode', width: 120 },
    { title: '物料名称', dataIndex: 'materialName', key: 'materialName', ellipsis: true },
    { title: '计划量', dataIndex: 'planQty', key: 'planQty', align: 'right', width: 90, render: (v: number) => fmtQty(v) },
    { title: '完工量', dataIndex: 'finishQty', key: 'finishQty', align: 'right', width: 90, render: (v: number) => fmtQty(v) },
    { title: '在制量', dataIndex: 'wipQty', key: 'wipQty', align: 'right', width: 90, render: (v: number) => fmtQty(v) },
    {
      title: '完工率',
      dataIndex: 'finishRate',
      key: 'finishRate',
      align: 'right',
      width: 150,
      render: (v: number, record: WorkExecutionRow) => (
        <Space size={4}>
          <Progress
            percent={Math.min(v, 100)}
            size="small"
            style={{ width: 80, marginBottom: 0 }}
            strokeColor={record.overdue ? '#ff4d4f' : '#52c41a'}
          />
          <span style={{ fontSize: 12 }}>{v.toFixed(1)}%</span>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'statusLabel',
      key: 'statusLabel',
      align: 'center',
      width: 90,
      render: (v: string, record: WorkExecutionRow) => (
        <Tag color={record.status === 'FINISHED' ? 'green' : record.status === 'PRODUCING' ? 'blue' : record.status === 'CLOSED' ? 'default' : 'orange'}>
          {v}
        </Tag>
      ),
    },
    { title: '计划开工', dataIndex: 'planStartDate', key: 'planStartDate', width: 105 },
    { title: '计划完工', dataIndex: 'planEndDate', key: 'planEndDate', width: 105 },
    {
      title: '超期',
      dataIndex: 'overdue',
      key: 'overdue',
      align: 'center',
      width: 80,
      render: (v: boolean) => (v ? <Tag color="red">超期</Tag> : <Tag color="green">正常</Tag>),
    },
  ]

  const rows = data?.rows ?? []
  const overdueCount = rows.filter((r) => r.overdue).length

  return (
    <Card
      title="生产订单执行跟踪"
      extra={
        <Button
          icon={<DownloadOutlined />}
          onClick={() => downloadReportCsv('/report/production/execution', params, 'wo_execution_all.csv')}
        >
          导出 CSV
        </Button>
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <DatePicker.RangePicker value={range} onChange={(v) => setRange(v ?? [null, null])} allowEmpty={[true, true]} />
        <Tag color={overdueCount > 0 ? 'red' : 'green'}>超期工单 {overdueCount} / {rows.length}</Tag>
      </Space>

      {isError && (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} message={`查询失败：${(error as Error)?.message ?? ''}`} />
      )}

      <Table<WorkExecutionRow>
        rowKey="orderId"
        size="middle"
        loading={isLoading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 20, showSizeChanger: false, showTotal: (t) => `共 ${t} 张工单` }}
      />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        口径：在制 = 计划量 − 完工量（负数截 0）；完工率 = 完工量 / 计划量 × 100%；
        超期 = 状态非「已完工/已关闭」且计划完工日期 &lt; 今天；区间过滤锚点 = 计划开工日期（空回退创建日期）。
      </Typography.Text>
    </Card>
  )
}
