import { Modal, Descriptions, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import StatusTag from '@/components/StatusTag'
import { fmtMoney, fmtPrice, fmtQty, type TransferDetail, type TransferLine } from '@/api/org'

const { Text } = Typography

interface DetailModalProps {
  detail: TransferDetail | null
  onClose: () => void
}

const lineColumns: ColumnsType<TransferLine> = [
  { title: '物料编码', dataIndex: 'materialCode', width: 130, render: (v) => v ?? '-' },
  { title: '物料名称', dataIndex: 'materialName', width: 150, render: (v) => v ?? '-' },
  { title: '数量', dataIndex: 'qty', width: 100, align: 'right', render: (v) => fmtQty(v) },
  {
    title: '成本单价',
    dataIndex: 'costPrice',
    width: 110,
    align: 'right',
    render: (v) => fmtPrice(v),
  },
  {
    title: '内部结算单价',
    dataIndex: 'settlePrice',
    width: 120,
    align: 'right',
    render: (v) => fmtPrice(v),
  },
  {
    title: '内部结算金额',
    dataIndex: 'settleAmount',
    width: 130,
    align: 'right',
    render: (v) => <Text strong>{fmtMoney(v)}</Text>,
  },
]

/** 调拨单详情弹窗（头信息 + 主子表明细 + 合计）。 */
export default function TransferDetailModal({ detail, onClose }: DetailModalProps) {
  return (
    <Modal
      title={`调拨单详情 - ${detail?.transferNo ?? ''}`}
      open={!!detail}
      onCancel={onClose}
      footer={null}
      destroyOnClose
      width={860}
    >
      {detail && (
        <>
          <Descriptions size="small" column={2} bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label="调拨单号">{detail.transferNo}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <StatusTag value={detail.statusText} />
            </Descriptions.Item>
            <Descriptions.Item label="调出工厂 / 仓库">
              {detail.fromFactoryName ?? '-'} / {detail.fromWarehouseName ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="调入工厂 / 仓库">
              {detail.toFactoryName ?? '-'} / {detail.toWarehouseName ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="审核时间">{detail.auditTime ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{detail.createTime ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="备注" span={2}>
              {detail.remark ?? '-'}
            </Descriptions.Item>
          </Descriptions>
          <Table<TransferLine>
            rowKey={(r) => r.id ?? `${r.materialId}-${r.qty}`}
            size="small"
            columns={lineColumns}
            dataSource={detail.lines}
            pagination={false}
            footer={() => (
              <div style={{ textAlign: 'right' }}>
                数量合计 {fmtQty(detail.totalQty)} · 内部结算金额合计{' '}
                <Text strong>{fmtMoney(detail.totalAmount)}</Text>
              </div>
            )}
          />
        </>
      )}
    </Modal>
  )
}
