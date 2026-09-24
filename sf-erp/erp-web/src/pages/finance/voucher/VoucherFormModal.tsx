import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import { createVoucher, getFinanceBookId, getSubjects, updateVoucher, type VoucherDetailVO } from '@/api/finance'

/** 分录行编辑态 */
interface EntryRow {
  key: number
  summary: string
  subjectId: number | null
  debit: number | null
  credit: number | null
}

/** 凭证表单弹窗属性（initial 有值 = 编辑草稿凭证） */
interface VoucherFormModalProps {
  open: boolean
  initial?: VoucherDetailVO | null
  onClose: () => void
}

let rowKeySeq = 1
/** 新建空白分录行 */
const newRow = (): EntryRow => ({ key: rowKeySeq++, summary: '', subjectId: null, debit: null, credit: null })

/**
 * 凭证录入/编辑弹窗：
 * 分录行可增删；底部实时"借方合计 / 贷方合计 / 差额"平衡条，
 * 借贷不平或行校验未过时禁止提交（后端仍会做 BigDecimal 精确校验兜底）。
 */
export default function VoucherFormModal({ open, initial, onClose }: VoucherFormModalProps) {
  const isEdit = initial != null
  const [voucherDate, setVoucherDate] = useState<Dayjs>(dayjs())
  const [word, setWord] = useState('记')
  const [attachmentCount, setAttachmentCount] = useState(0)
  const [remark, setRemark] = useState('')
  const [rows, setRows] = useState<EntryRow[]>([newRow(), newRow()])
  const queryClient = useQueryClient()

  // 打开时重置表单（编辑场景用凭证明细回填）
  useEffect(() => {
    if (!open) return
    if (initial) {
      setVoucherDate(dayjs(initial.voucherDate))
      setWord(initial.word)
      setAttachmentCount(initial.attachmentCount)
      setRemark(initial.remark ?? '')
      setRows(
        initial.entries.map((e) => ({
          key: rowKeySeq++,
          summary: e.summary,
          subjectId: e.subjectId,
          debit: e.debitAmount || null,
          credit: e.creditAmount || null,
        })),
      )
    } else {
      setVoucherDate(dayjs())
      setWord('记')
      setAttachmentCount(0)
      setRemark('')
      setRows([newRow(), newRow()])
    }
  }, [open, initial])

  // 科目随当前账套隔离（key 带账套防跨账套串缓存）
  const { data: subjects } = useQuery({
    queryKey: ['finance', 'subjects', getFinanceBookId()],
    queryFn: () => getSubjects(),
    enabled: open,
  })
  // 仅启用状态的末级科目可作为分录科目（与金蝶总账一致）
  const subjectOptions = useMemo(
    () =>
      (subjects ?? [])
        .filter((s) => s.leaf && s.status === 1)
        .map((s) => ({ value: s.id, label: `${s.code} ${s.name}` })),
    [subjects],
  )

  const totalDebit = useMemo(
    () => rows.reduce((sum, r) => sum + (r.debit || 0), 0),
    [rows],
  )
  const totalCredit = useMemo(
    () => rows.reduce((sum, r) => sum + (r.credit || 0), 0),
    [rows],
  )
  const diff = Number((totalDebit - totalCredit).toFixed(2))
  const balanced = Math.abs(diff) < 0.005 && totalDebit > 0
  const rowsValid = rows.length >= 2 && rows.every((r) => r.summary.trim() !== '' && r.subjectId != null
    && ((r.debit || 0) > 0) !== ((r.credit || 0) > 0))

  const saveMutation = useMutation({
    mutationFn: async () => {
      const body = {
        voucherDate: voucherDate.format('YYYY-MM-DD'),
        word,
        attachmentCount,
        remark: remark || undefined,
        entries: rows.map((r) => ({
          summary: r.summary.trim(),
          subjectId: r.subjectId as number,
          debitAmount: r.debit || 0,
          creditAmount: r.credit || 0,
        })),
      }
      if (isEdit && initial) {
        await updateVoucher(initial.id, body)
      } else {
        await createVoucher(body)
      }
    },
    onSuccess: () => {
      message.success(isEdit ? '凭证修改成功' : '凭证保存成功')
      queryClient.invalidateQueries({ queryKey: ['finance', 'vouchers'] })
      onClose()
    },
  })

  const updateRow = (key: number, patch: Partial<EntryRow>) =>
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, ...patch } : r)))

  /** 输入借方时清空贷方（借贷互斥），反之亦然 */
  const setDebit = (key: number, v: number | null) =>
    updateRow(key, v ? { debit: v, credit: null } : { debit: null })
  const setCredit = (key: number, v: number | null) =>
    updateRow(key, v ? { credit: v, debit: null } : { credit: null })

  const columns: ColumnsType<EntryRow> = [
    {
      title: '摘要',
      key: 'summary',
      width: 220,
      render: (_, row) => (
        <Input
          value={row.summary}
          onChange={(e) => updateRow(row.key, { summary: e.target.value })}
          placeholder="分录摘要"
          maxLength={200}
        />
      ),
    },
    {
      title: '会计科目',
      key: 'subject',
      width: 240,
      render: (_, row) => (
        <Select
          showSearch
          optionFilterProp="label"
          style={{ width: '100%' }}
          placeholder="选择末级科目"
          value={row.subjectId}
          onChange={(v) => updateRow(row.key, { subjectId: v })}
          options={subjectOptions}
        />
      ),
    },
    {
      title: '借方金额',
      key: 'debit',
      width: 140,
      render: (_, row) => (
        <InputNumber
          min={0}
          step={0.01}
          precision={2}
          style={{ width: '100%' }}
          value={row.debit}
          placeholder="0.00"
          onChange={(v) => setDebit(row.key, v)}
        />
      ),
    },
    {
      title: '贷方金额',
      key: 'credit',
      width: 140,
      render: (_, row) => (
        <InputNumber
          min={0}
          step={0.01}
          precision={2}
          style={{ width: '100%' }}
          value={row.credit}
          placeholder="0.00"
          onChange={(v) => setCredit(row.key, v)}
        />
      ),
    },
    {
      title: '',
      key: 'op',
      width: 60,
      render: (_, row) => (
        <Button
          type="text"
          danger
          size="small"
          disabled={rows.length <= 2}
          onClick={() => setRows((prev) => prev.filter((r) => r.key !== row.key))}
        >
          删除
        </Button>
      ),
    },
  ]

  return (
    <Modal
      open={open}
      title={isEdit ? `修改凭证 ${initial?.voucherNo ?? ''}` : '新增凭证'}
      width={960}
      footer={null}
      onCancel={onClose}
      destroyOnClose
    >
      <Form layout="inline" style={{ marginBottom: 12, rowGap: 8 }}>
        <Form.Item label="凭证日期" required>
          <DatePicker value={voucherDate} onChange={(d) => d && setVoucherDate(d)} allowClear={false} />
        </Form.Item>
        <Form.Item label="凭证字" required>
          <Select style={{ width: 90 }} value={word} onChange={setWord} options={['记', '收', '付', '转'].map((w) => ({ value: w, label: w }))} />
        </Form.Item>
        <Form.Item label="附件张数">
          <InputNumber min={0} precision={0} value={attachmentCount} onChange={(v) => setAttachmentCount(v || 0)} />
        </Form.Item>
        <Form.Item label="备注">
          <Input style={{ width: 240 }} value={remark} onChange={(e) => setRemark(e.target.value)} maxLength={200} />
        </Form.Item>
      </Form>

      <Table<EntryRow>
        rowKey="key"
        size="small"
        columns={columns}
        dataSource={rows}
        pagination={false}
        footer={() => (
          <Button type="dashed" block onClick={() => setRows((prev) => [...prev, newRow()])}>
            + 添加分录
          </Button>
        )}
      />

      {/* 实时借贷平衡条：不平禁止提交 */}
      <div
        style={{
          marginTop: 12,
          padding: '10px 16px',
          borderRadius: 8,
          background: balanced ? '#f6ffed' : '#fff2f0',
          border: `1px solid ${balanced ? '#b7eb8f' : '#ffccc7'}`,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}
      >
        <Space size={24}>
          <Typography.Text>借方合计：</Typography.Text>
          <Typography.Text strong>{totalDebit.toFixed(2)}</Typography.Text>
          <Typography.Text>贷方合计：</Typography.Text>
          <Typography.Text strong>{totalCredit.toFixed(2)}</Typography.Text>
          <Typography.Text>差额：</Typography.Text>
          <Typography.Text strong type={balanced ? 'success' : 'danger'}>
            {diff.toFixed(2)}
          </Typography.Text>
        </Space>
        <Space>
          {!rowsValid && rows.length > 0 ? (
            <Typography.Text type="warning" style={{ fontSize: 12 }}>
              每行需：摘要 + 末级科目 + 借贷单边金额
            </Typography.Text>
          ) : null}
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            loading={saveMutation.isPending}
            disabled={!balanced || !rowsValid}
            onClick={() => saveMutation.mutate()}
          >
            保存
          </Button>
        </Space>
      </div>
      {!balanced ? (
        <Alert
          style={{ marginTop: 8 }}
          type="error"
          showIcon
          message="借贷不平衡，无法保存凭证（借方合计必须等于贷方合计）"
        />
      ) : null}
    </Modal>
  )
}
