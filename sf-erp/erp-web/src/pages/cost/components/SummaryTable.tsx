import { Card, Col, Row, Statistic, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { fmtAmount, fmtQty, type MaterialSummaryRow, type SettleSummary } from '@/api/cost'

/**
 * 物料收发存汇总表（月末核算 / 成本分析共用）。
 * 顶部四项金额指标（期初/入库/出库/期末）+ 明细表，金额右对齐。
 */
export default function SummaryTable({
  summary,
  loading,
}: {
  summary: SettleSummary | undefined
  loading: boolean
}) {
  const columns: ColumnsType<MaterialSummaryRow> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 130, render: (v) => v ?? '-' },
    { title: '物料名称', dataIndex: 'materialName', ellipsis: true, render: (v) => v ?? '-' },
    { title: '期初数量', dataIndex: 'beginQty', align: 'right', width: 100, render: fmtQty },
    { title: '期初金额', dataIndex: 'beginAmount', align: 'right', width: 120, render: fmtAmount },
    { title: '入库数量', dataIndex: 'inQty', align: 'right', width: 100, render: fmtQty },
    { title: '入库金额', dataIndex: 'inAmount', align: 'right', width: 120, render: fmtAmount },
    { title: '出库数量', dataIndex: 'outQty', align: 'right', width: 100, render: fmtQty },
    { title: '出库成本', dataIndex: 'outAmount', align: 'right', width: 120, render: fmtAmount },
    { title: '期末数量', dataIndex: 'endQty', align: 'right', width: 100, render: fmtQty },
    { title: '期末金额', dataIndex: 'endAmount', align: 'right', width: 130, render: fmtAmount },
  ]

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} md={6}>
          <Card className="erp-hover-card" styles={{ body: { padding: 16 } }}>
            <Statistic title="期初金额" value={summary?.totalBeginAmount ?? 0} precision={2} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card className="erp-hover-card" styles={{ body: { padding: 16 } }}>
            <Statistic title="本期入库" value={summary?.totalInAmount ?? 0} precision={2} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card className="erp-hover-card" styles={{ body: { padding: 16 } }}>
            <Statistic
              title="本期出库成本"
              value={summary?.totalOutAmount ?? 0}
              precision={2}
              valueStyle={{ color: '#cf1322' }}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card className="erp-hover-card" styles={{ body: { padding: 16 } }}>
            <Statistic
              title="期末金额"
              value={summary?.totalEndAmount ?? 0}
              precision={2}
              valueStyle={{ color: '#1668dc' }}
            />
          </Card>
        </Col>
      </Row>

      {summary && (
        <div style={{ marginBottom: 8 }}>
          <Tag color={summary.status === 'CLOSED' ? 'green' : 'blue'}>
            {summary.status === 'CLOSED' ? '已结账' : '未结账'}
          </Tag>
        </div>
      )}

      <Table<MaterialSummaryRow>
        rowKey="materialId"
        columns={columns}
        dataSource={summary?.rows ?? []}
        loading={loading}
        size="small"
        pagination={(summary?.rows.length ?? 0) > 20 ? { pageSize: 20, showTotal: (t) => `共 ${t} 条` } : false}
        locale={{ emptyText: '该期间暂无计价流水' }}
      />
    </div>
  )
}
