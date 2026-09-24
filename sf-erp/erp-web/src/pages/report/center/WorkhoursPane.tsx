import { useMemo, useState } from 'react'
import { Alert, Button, Card, DatePicker, Select, Space, Table, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import { downloadReportCsv, getWorkhours, type WorkhoursRow } from '@/api/report'
import { fmtQty } from '@/pages/report/shared'
import { workCenterApi } from '@/api/mrp'
/** 聚合维度选项 */
const GROUP_OPTIONS = [
  { value: 'operation', label: '按工序' },
  { value: 'employee', label: '按员工' },
]

type GroupBy = 'operation' | 'employee'

/** 默认查询区间：当月 1 日 ~ 今天 */
function defaultRange(): [Dayjs, Dayjs] {
  const today = dayjs()
  return [today.startOf('month'), today]
}

/**
 * 报工工时统计：工序/员工 合格数/不良数/不良率/工时（mrp_work_report）。
 */
export default function WorkhoursPane() {
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null]>(defaultRange())
  const [workCenterId, setWorkCenterId] = useState<number | undefined>(undefined)
  const [groupBy, setGroupBy] = useState<GroupBy>('operation')

  const params = useMemo(
    () => ({
      from: range[0]?.format('YYYY-MM-DD'),
      to: range[1]?.add(1, 'day').format('YYYY-MM-DD'),
      workCenterId,
      groupBy,
    }),
    [range, workCenterId, groupBy],
  )

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['report', 'workhours', params],
    queryFn: () => getWorkhours(params),
  })

  const { data: workCenters } = useQuery({
    queryKey: ['report', 'options', 'work-centers'],
    queryFn: workCenterApi.listAll,
  })

  const columns: ColumnsType<WorkhoursRow> = useMemo(() => {
    const dimCols: ColumnsType<WorkhoursRow> =
      groupBy === 'employee'
        ? [{ title: '员工', dataIndex: 'dimName', key: 'dimName', width: 140 }]
        : [
            { title: '工序编码', dataIndex: 'dimCode', key: 'dimCode', width: 140 },
            { title: '工序名称', dataIndex: 'dimName', key: 'dimName', ellipsis: true },
          ]
    return [
      ...dimCols,
      {
        title: groupBy === 'employee' ? '涉及工序数' : '报工员工数',
        dataIndex: 'extraCount',
        key: 'extraCount',
        align: 'right',
        width: 110,
      },
      { title: '合格数', dataIndex: 'goodQty', key: 'goodQty', align: 'right', width: 100, render: (v: number) => fmtQty(v) },
      { title: '不良数', dataIndex: 'badQty', key: 'badQty', align: 'right', width: 100, render: (v: number) => fmtQty(v) },
      { title: '不良率', dataIndex: 'badRate', key: 'badRate', align: 'right', width: 100, render: (v: number) => `${v.toFixed(2)}%` },
      { title: '工时(分钟)', dataIndex: 'workMinutes', key: 'workMinutes', align: 'right', width: 110 },
      {
        title: '工时(小时)',
        dataIndex: 'workHours',
        key: 'workHours',
        align: 'right',
        width: 110,
        render: (v: number) => <strong>{v.toFixed(2)}</strong>,
      },
    ]
  }, [groupBy])

  const rows = data?.rows ?? []
  const totalMinutes = rows.reduce((acc, r) => acc + (r.workMinutes ?? 0), 0)

  return (
    <Card title="报工工时统计">
      <Space wrap style={{ marginBottom: 16 }}>
        <DatePicker.RangePicker value={range} onChange={(v) => setRange(v ?? [null, null])} allowEmpty={[true, true]} />
        <Select
          style={{ width: 200 }}
          placeholder="全部工作中心"
          allowClear
          showSearch
          optionFilterProp="label"
          value={workCenterId}
          onChange={setWorkCenterId}
          options={(workCenters ?? []).map((w) => ({ value: w.id, label: w.name }))}
        />
        <Select style={{ width: 120 }} value={groupBy} onChange={(v) => setGroupBy(v as GroupBy)} options={GROUP_OPTIONS} />
        <Button
          icon={<DownloadOutlined />}
          onClick={() => downloadReportCsv('/report/production/workhours', params, `workhours_${groupBy}.csv`)}
        >
          导出 CSV
        </Button>
      </Space>

      {isError && (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} message={`查询失败：${(error as Error)?.message ?? ''}`} />
      )}

      <Table<WorkhoursRow>
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
                <Table.Summary.Cell index={0} colSpan={groupBy === 'employee' ? 1 : 2}>
                  <strong>合计</strong>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={1} align="right"><strong>-</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={2} align="right"><strong>{fmtQty(rows.reduce((a, r) => a + r.goodQty, 0))}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={3} align="right"><strong>{fmtQty(rows.reduce((a, r) => a + r.badQty, 0))}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={4} align="right"><strong>-</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={5} align="right"><strong>{totalMinutes}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={6} align="right"><strong>{(totalMinutes / 60).toFixed(2)}</strong></Table.Summary.Cell>
              </Table.Summary.Row>
            </Table.Summary>
          )
        }
      />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        口径：按工序报工单（mrp_work_report）汇总，区间按报工时间（report_time）落期；
        工作中心过滤经工单工序（mrp_wo_step.work_center_id）关联；工时 = 报工分钟合计。
      </Typography.Text>
    </Card>
  )
}
