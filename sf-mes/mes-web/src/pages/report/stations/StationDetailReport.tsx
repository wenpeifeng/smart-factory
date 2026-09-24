import { useQuery } from '@tanstack/react-query'
import { Button, Table } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { DownloadOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import HasPerm from '@/components/HasPerm'
import { getStationDetail } from '@/api/report'

type Row = Record<string, unknown>

/** 报工明细报表（近 7 天），支持 CSV 导出 */
export default function StationDetailReport() {
  const { data, isLoading } = useQuery({ queryKey: ['report', 'stations'], queryFn: () => getStationDetail(7) })

  const columns: ColumnsType<Row> = [
    { title: '记录号', dataIndex: 'recordNo', width: 160 },
    { title: '工单号', dataIndex: 'woNo', width: 150 },
    { title: '工序', dataIndex: 'operationName' },
    { title: '合格', dataIndex: 'goodQty', align: 'right' },
    { title: '报废', dataIndex: 'scrapQty', align: 'right' },
    { title: '返修', dataIndex: 'reworkQty', align: 'right' },
    { title: '报工人', dataIndex: 'workerName', width: 100 },
    { title: '过站时间', dataIndex: 'stationTime', width: 170 },
    { title: '结果', dataIndex: 'result', render: (v: string) => <StatusTag value={v === 'PASS' ? '合格' : '不合格'} semantic={v === 'PASS' ? 'success' : 'error'} /> },
  ]

  return (
    <>
      <PageHeader
        title="报工明细报表"
        subtitle="近 7 天过站报工明细（导出默认近 30 天）"
        extra={
          <HasPerm code="report:station:export">
            <Button icon={<DownloadOutlined />} href="/api/report/station-detail/csv?days=30" target="_blank">导出 CSV</Button>
          </HasPerm>
        }
      />
      <Table<Row>
        rowKey="recordNo"
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
