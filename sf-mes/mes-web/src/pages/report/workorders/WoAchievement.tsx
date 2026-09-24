import { useQuery } from '@tanstack/react-query'
import { Button, Table } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { DownloadOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import HasPerm from '@/components/HasPerm'
import { WO_STATUS_LABEL, getWoAchievement } from '@/api/report'

type Row = Record<string, unknown>

/** 工单达成报表：计划 vs 完工，支持 CSV 导出 */
export default function WoAchievement() {
  const { data, isLoading } = useQuery({ queryKey: ['report', 'wo'], queryFn: () => getWoAchievement() })

  const columns: ColumnsType<Row> = [
    { title: '工单号', dataIndex: 'woNo', width: 160 },
    { title: '产品', dataIndex: 'materialName', render: (v) => v ?? '-' },
    { title: '计划数量', dataIndex: 'planQty', align: 'right' },
    { title: '完工数量', dataIndex: 'finishQty', align: 'right' },
    { title: '报废数量', dataIndex: 'scrapQty', align: 'right' },
    {
      title: '达成率',
      render: (_, r) => {
        const plan = Number(r.planQty ?? 0)
        return plan > 0 ? `${((Number(r.finishQty ?? 0) / plan) * 100).toFixed(1)}%` : '-'
      },
      align: 'right',
    },
    { title: '状态', dataIndex: 'status', render: (v: string) => <StatusTag value={WO_STATUS_LABEL[v] ?? v} /> },
    { title: '计划开工', dataIndex: 'planDate', width: 110 },
  ]

  return (
    <>
      <PageHeader
        title="工单达成报表"
        subtitle="计划数量 vs 完工/报废的达成情况"
        extra={
          <HasPerm code="report:workorder:export">
            <Button icon={<DownloadOutlined />} href="/api/report/workorder-achievement/csv" target="_blank">导出 CSV</Button>
          </HasPerm>
        }
      />
      <Table<Row>
        rowKey="woNo"
        size="small"
        columns={columns}
        dataSource={data ?? []}
        loading={isLoading}
        pagination={{ pageSize: 50, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
      />
    </>
  )
}
