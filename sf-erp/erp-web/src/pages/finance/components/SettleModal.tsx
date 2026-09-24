import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, DatePicker, Form, Input, InputNumber, Modal, Select, Space, Table, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import {
  createPayment,
  createReceipt,
  getAccounts,
  getFinanceBookId,
  pagePayables,
  pageReceivables,
  type AccountVO,
  type SettleStatus,
} from '@/api/finance'

/** 核销弹窗内统一行结构（应收/应付字段同构） */
interface SettleRow {
  id: number
  bizNo: string
  bizDate: string | null
  amount: number
  settledAmount: number
  unsettledAmount: number
  status: SettleStatus
}

/** 核销弹窗属性 */
interface SettleModalProps {
  /** 弹窗开关 */
  open: boolean
  /** 核销类型：receipt=收款核销应收 / payment=付款核销应付 */
  kind: 'receipt' | 'payment'
  /** 客户/供应商 ID（为空时弹窗不请求数据） */
  partnerId: number | null
  onClose: () => void
}

const COLUMNS: ColumnsType<SettleRow> = [
  { title: '来源单号', dataIndex: 'bizNo', width: 170 },
  { title: '业务日期', dataIndex: 'bizDate', width: 110, render: (v: string | null) => v ?? '-' },
  { title: '应收/应付金额', dataIndex: 'amount', align: 'right', width: 120, render: (v: number) => v.toFixed(2) },
  { title: '已核销', dataIndex: 'settledAmount', align: 'right', width: 100, render: (v: number) => v.toFixed(2) },
]

/**
 * 收/付款核销弹窗：
 * 选择资金账户与业务日期，对该客商的未核销完结单据逐笔分配核销额，
 * 底部实时合计；保存后由后端在同一事务内回写核销状态并更新账户余额。
 */
export default function SettleModal({ open, kind, partnerId, onClose }: SettleModalProps) {
  const isReceipt = kind === 'receipt'
  const [accountId, setAccountId] = useState<number | null>(null)
  const [bizDate, setBizDate] = useState<Dayjs>(dayjs())
  const [remark, setRemark] = useState('')
  /** 应收/应付 ID -> 本次核销额 */
  const [allocations, setAllocations] = useState<Record<number, number>>({})
  const queryClient = useQueryClient()

  // 打开/切换客商时清空上次分配
  useEffect(() => {
    if (open) {
      setAllocations({})
      setRemark('')
    }
  }, [open, partnerId])

  // 账户/单据随当前账套隔离（key 带账套防跨账套串缓存）
  const { data: accounts } = useQuery({
    queryKey: ['finance', 'accounts', getFinanceBookId()],
    queryFn: getAccounts,
    enabled: open,
  })
  const enabledAccounts: AccountVO[] = useMemo(
    () => (accounts ?? []).filter((a) => a.status === 1),
    [accounts],
  )

  const { data: rowsData, isLoading } = useQuery({
    queryKey: ['finance', kind, 'unsettled', getFinanceBookId(), partnerId],
    queryFn: async () => {
      if (isReceipt) {
        const page = await pageReceivables({ customerId: partnerId ?? 0, unsettledOnly: true, pageSize: 100 })
        return page.records as SettleRow[]
      }
      const page = await pagePayables({ supplierId: partnerId ?? 0, unsettledOnly: true, pageSize: 100 })
      return page.records as SettleRow[]
    },
    enabled: open && partnerId != null,
  })
  const rows = rowsData ?? []

  const total = useMemo(
    () => Object.values(allocations).reduce((sum, v) => sum + (v || 0), 0),
    [allocations],
  )

  const saveMutation = useMutation({
    mutationFn: () => {
      const items = Object.entries(allocations)
        .filter(([, amount]) => amount > 0)
        .map(([id, amount]) => ({
          [isReceipt ? 'receivableId' : 'payableId']: Number(id),
          verifyAmount: Number(amount.toFixed(2)),
        }))
      const body = {
        [isReceipt ? 'customerId' : 'supplierId']: partnerId,
        accountId,
        bizDate: bizDate.format('YYYY-MM-DD'),
        amount: Number(total.toFixed(2)),
        remark: remark || undefined,
        items,
      } as never
      return isReceipt ? createReceipt(body) : createPayment(body)
    },
    onSuccess: () => {
      message.success(isReceipt ? '收款核销成功' : '付款核销成功')
      // 失效相关列表缓存，页面自动刷新
      queryClient.invalidateQueries({ queryKey: ['finance', 'receivables'] })
      queryClient.invalidateQueries({ queryKey: ['finance', 'payables'] })
      queryClient.invalidateQueries({ queryKey: ['finance', 'accounts'] })
      onClose()
    },
  })

  const columns: ColumnsType<SettleRow> = [
    ...COLUMNS,
    {
      title: '未核销余额',
      dataIndex: 'unsettledAmount',
      align: 'right',
      width: 110,
      render: (v: number) => v.toFixed(2),
    },
    {
      title: '本次核销额',
      key: 'verify',
      width: 150,
      render: (_, row) => (
        <InputNumber
          min={0}
          max={row.unsettledAmount}
          step={0.01}
          precision={2}
          value={allocations[row.id] ?? null}
          placeholder="0.00"
          onChange={(v) =>
            setAllocations((prev) => ({ ...prev, [row.id]: Math.min(v || 0, row.unsettledAmount) }))
          }
          disabled={row.status === 'SETTLED'}
        />
      ),
    },
  ]

  const canSubmit = accountId != null && partnerId != null && total > 0

  return (
    <Modal
      open={open}
      title={isReceipt ? '收款核销' : '付款核销'}
      width={860}
      onCancel={onClose}
      onOk={() => saveMutation.mutate()}
      okText="保存核销"
      confirmLoading={saveMutation.isPending}
      okButtonProps={{ disabled: !canSubmit }}
      destroyOnClose
    >
      <Form layout="inline" style={{ marginBottom: 12, rowGap: 8 }}>
        <Form.Item label={isReceipt ? '收款账户' : '付款账户'} required>
          <Select
            style={{ width: 220 }}
            placeholder="选择资金账户"
            value={accountId}
            onChange={setAccountId}
            options={enabledAccounts.map((a) => ({
              value: a.id,
              label: `${a.name}（余额 ${a.currentBalance.toFixed(2)}）`,
            }))}
          />
        </Form.Item>
        <Form.Item label={isReceipt ? '收款日期' : '付款日期'} required>
          <DatePicker value={bizDate} onChange={(d) => d && setBizDate(d)} allowClear={false} />
        </Form.Item>
        <Form.Item label="备注">
          <Input style={{ width: 200 }} value={remark} onChange={(e) => setRemark(e.target.value)} maxLength={200} />
        </Form.Item>
      </Form>

      {rows.length === 0 && !isLoading ? (
        <Alert type="info" showIcon message="该客商没有待核销的应收/应付单" style={{ marginBottom: 12 }} />
      ) : null}

      <Table<SettleRow>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={isLoading}
        pagination={false}
        scroll={{ y: 320 }}
      />

      <Space style={{ marginTop: 12, width: '100%', justifyContent: 'flex-end' }}>
        <Typography.Text>本次{isReceipt ? '收款' : '付款'}合计：</Typography.Text>
        <Typography.Text strong style={{ fontSize: 16, color: total > 0 ? '#1668dc' : undefined }}>
          {total.toFixed(2)}
        </Typography.Text>
      </Space>
    </Modal>
  )
}
