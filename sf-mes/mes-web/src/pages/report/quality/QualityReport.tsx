import { useQuery } from '@tanstack/react-query'
import { Table } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import PageHeader from '@/components/PageHeader'
import { getQualitySummary } from '@/api/report'

type Row = Record<string, unknown>

/** 质量统计报表：按物料汇总抽检/合格/不合格与合格率 */
export default function QualityReport() {
  const { data, isLoading } = useQuery({ queryKey: ['report', 'quality'], queryFn: getQualitySummary })

  const columns: ColumnsType<Row> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 140 },
    { title: '物料名称', dataIndex: 'materialName', render: (v) => v ?? '-' },
    { title: '抽检数量', dataIndex: 'sampleQty', align: 'right' },
    { title: '合格', dataIndex: 'qualifiedQty', align: 'right' },
    { title: '不合格', dataIndex: 'unqualifiedQty', align: 'right' },
    {
      title: '合格率',
      align: 'right',
      render: (_, r) => {
        const s = Number(r.sampleQty ?? 0)
        return s > 0 ? `${((Number(r.qualifiedQty ?? 0) / s) * 100).toFixed(1)}%` : '-'
      },
    },
  ]

  return (
    <>
      <PageHeader title="质量统计报表" subtitle="按物料汇总的检验合格率（仅统计已提交检验单）" />
      <Table<Row>
        rowKey="materialCode"
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
