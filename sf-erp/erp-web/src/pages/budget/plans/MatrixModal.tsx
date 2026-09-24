import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { App, Button, InputNumber, Modal, Popconfirm, Select, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { planApi, optionsApi, type BudgetPlanRow, type MatrixRowInput, type MatrixRowVO } from '@/api/budget'
import { useSubmitting } from '@/hooks/useSubmitting'
import { fmtMoney, MONTH_INDEXES } from '../shared'

/** 可编辑矩阵行：部门×科目 + 12 个月金额（null=未填，保存按 0） */
interface EditRow {
  key: string
  deptId?: number
  subjectId?: number
  amounts: (number | null)[]
}

let rowSeq = 0 // 行临时 key 自增，弹窗生命周期内唯一即可

/** 读取矩阵行 VO 的第 n 月金额（m1..m12 字段） */
function monthOf(row: MatrixRowVO, month: number): number | null {
  return (row as unknown as Record<string, number | null>)[`m${month}`]
}

/**
 * 预算编制弹窗 —— 部门×科目 矩阵录入 12 个月金额。
 * <p>
 * - 可编辑表格：每行选部门/科目 + 12 个月 InputNumber（精度 2），行合计/列合计实时计算；
 * - 仅草稿(DRAFT)可编辑保存；已下达/已关闭只读查看；
 * - 保存整体提交：未出现在表格中的部门×科目组合将被删除。
 */
export default function MatrixModal({
  plan,
  open,
  onClose,
  onSuccess,
}: {
  plan: BudgetPlanRow | null
  open: boolean
  onClose: () => void
  onSuccess: () => void
}) {
  const { message } = App.useApp()
  const editable = plan?.status === 'DRAFT'

  const [rows, setRows] = useState<EditRow[]>([])

  const { data: matrix, isLoading } = useQuery({
    queryKey: ['budget', 'matrix', plan?.id],
    queryFn: () => planApi.matrix(plan!.id),
    enabled: open && !!plan,
  })

  const { data: options } = useQuery({
    queryKey: ['budget', 'options'],
    queryFn: optionsApi.get,
    enabled: open,
  })

  // 矩阵数据回显 → 可编辑行
  useEffect(() => {
    if (matrix) {
      setRows(
        matrix.rows.map((r) => ({
          key: `row-${r.deptId}-${r.subjectId}`,
          deptId: r.deptId,
          subjectId: r.subjectId,
          amounts: MONTH_INDEXES.map((i) => monthOf(r, i + 1)),
        })),
      )
    }
  }, [matrix])

  /** 部门下拉（子部门缩进展示） */
  const deptOptions = (options?.depts ?? []).map((d) => ({
    value: d.id,
    label: d.parentId === 0 ? d.name : `　└ ${d.name}`,
    disabled: d.status === 0,
  }))

  /** 科目下拉（仅末级费用/成本类） */
  const subjectOptions = (options?.subjects ?? []).map((s) => ({
    value: s.id,
    label: `${s.code} ${s.name}`,
  }))

  const updateRow = (key: string, patch: Partial<EditRow>) =>
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, ...patch } : r)))

  const updateAmount = (key: string, index: number, value: number | null) =>
    setRows((prev) =>
      prev.map((r) => {
        if (r.key !== key) return r
        const amounts = [...r.amounts]
        amounts[index] = value
        return { ...r, amounts }
      }),
    )

  const addRow = () => setRows((prev) => [...prev, { key: `new-${++rowSeq}`, amounts: Array(12).fill(null) }])

  const removeRow = (key: string) => setRows((prev) => prev.filter((r) => r.key !== key))

  /** 行合计（未填按 0） */
  const rowTotal = (row: EditRow) => row.amounts.reduce<number>((sum, v) => sum + (v ?? 0), 0)

  /** 列合计（各月） */
  const columnTotals = MONTH_INDEXES.map((i) => rows.reduce((sum, r) => sum + (r.amounts[i] ?? 0), 0))
  const grandTotal = columnTotals.reduce((sum, v) => sum + v, 0)

  /** 本地校验：部门/科目必选且组合不重复（通过后才走提交） */
  const validate = (): string | null => {
    const seen = new Set<string>()
    for (const r of rows) {
      if (!r.deptId || !r.subjectId) return '存在未选择部门或科目的明细行，请补全后再保存'
      const key = `${r.deptId}-${r.subjectId}`
      if (seen.has(key)) return '存在重复的部门×科目明细行，请合并后再保存'
      seen.add(key)
    }
    return null
  }

  const submit = useSubmitting(
    async () => {
      if (!plan) return
      const payload = rows.map((r) => {
        const months = Object.fromEntries(MONTH_INDEXES.map((i) => [`m${i + 1}`, r.amounts[i] ?? 0]))
        return { deptId: r.deptId!, subjectId: r.subjectId!, ...months } as MatrixRowInput
      })
      await planApi.saveMatrix(plan.id, payload)
    },
    {
      successText: '预算明细已保存',
      onSuccess: () => {
        onSuccess()
        onClose()
      },
    },
  )

  const handleSave = () => {
    const error = validate()
    if (error) {
      message.error(error)
      return
    }
    submit.run()
  }

  const columns: ColumnsType<EditRow> = [
    {
      title: '部门',
      dataIndex: 'deptId',
      width: 160,
      fixed: 'left',
      render: (_, row) =>
        editable ? (
          <Select
            size="small"
            style={{ width: '100%' }}
            placeholder="选择部门"
            showSearch
            optionFilterProp="label"
            value={row.deptId}
            options={deptOptions}
            onChange={(v) => updateRow(row.key, { deptId: v })}
          />
        ) : (
          (options?.depts.find((d) => d.id === row.deptId)?.name ?? `部门#${row.deptId}`)
        ),
    },
    {
      title: '预算科目',
      dataIndex: 'subjectId',
      width: 185,
      fixed: 'left',
      render: (_, row) => {
        const subject = options?.subjects.find((s) => s.id === row.subjectId)
        const text = subject ? `${subject.code} ${subject.name}` : `科目#${row.subjectId}`
        return editable ? (
          <Select
            size="small"
            style={{ width: '100%' }}
            placeholder="末级费用/成本类科目"
            showSearch
            optionFilterProp="label"
            value={row.subjectId}
            options={subjectOptions}
            onChange={(v) => updateRow(row.key, { subjectId: v })}
          />
        ) : (
          text
        )
      },
    },
    ...MONTH_INDEXES.map<ColumnsType<EditRow>[number]>((i) => ({
      title: `${i + 1}月`,
      width: 96,
      align: 'right' as const,
      render: (_, row) =>
        editable ? (
          <InputNumber
            size="small"
            style={{ width: '100%' }}
            min={0}
            precision={2}
            value={row.amounts[i]}
            onChange={(v) => updateAmount(row.key, i, v)}
          />
        ) : (
          fmtMoney(row.amounts[i] ?? 0)
        ),
    })),
    {
      title: '行合计',
      width: 110,
      align: 'right',
      fixed: 'right',
      render: (_, row) => <b>{fmtMoney(rowTotal(row))}</b>,
    },
    ...(editable
      ? [
          {
            title: '操作',
            width: 70,
            fixed: 'right' as const,
            render: (_: unknown, row: EditRow) => (
              <Popconfirm title="删除该行预算明细？" onConfirm={() => removeRow(row.key)}>
                <Button type="link" size="small" danger>
                  删除
                </Button>
              </Popconfirm>
            ),
          } as ColumnsType<EditRow>[number],
        ]
      : []),
  ]

  return (
    <Modal
      title={
        <span>
          预算编制 — {plan?.planName}
          <Typography.Text type="secondary" style={{ marginLeft: 8, fontSize: 13 }}>
            {plan?.budgetYear} 年度 · {plan?.statusText}
            {!editable && '（只读）'}
          </Typography.Text>
        </span>
      }
      open={open}
      onCancel={onClose}
      width={1280}
      footer={
        editable
          ? [
              <Button key="cancel" onClick={onClose}>
                取消
              </Button>,
              <Popconfirm
                key="save"
                title="保存预算明细？"
                description="整体保存：未列出的部门×科目组合将被删除"
                onConfirm={handleSave}
              >
                <Button type="primary" loading={submit.loading} disabled={submit.loading}>
                  保存
                </Button>
              </Popconfirm>,
            ]
          : [
              <Button key="close" onClick={onClose}>
                关闭
              </Button>,
            ]
      }
    >
      {editable && (
        <div style={{ marginBottom: 12 }}>
          <Button size="small" onClick={addRow}>
            + 添加部门×科目行
          </Button>
          <Typography.Text type="secondary" style={{ marginLeft: 12, fontSize: 12 }}>
            按部门×科目逐行编制 1-12 月预算金额，行合计/列合计实时计算；科目仅限末级费用/成本类
          </Typography.Text>
        </div>
      )}
      <Table<EditRow>
        rowKey="key"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={isLoading}
        scroll={{ x: 1680 }}
        pagination={false}
        summary={() => (
          <Table.Summary fixed>
            <Table.Summary.Row style={{ background: '#fafafa', fontWeight: 600 }}>
              <Table.Summary.Cell index={0} colSpan={2}>
                列合计
              </Table.Summary.Cell>
              {columnTotals.map((v, i) => (
                <Table.Summary.Cell key={i} index={i + 2} align="right">
                  {fmtMoney(v)}
                </Table.Summary.Cell>
              ))}
              <Table.Summary.Cell index={14} align="right">
                <span style={{ color: '#1668dc' }}>{fmtMoney(grandTotal)}</span>
              </Table.Summary.Cell>
              {editable && <Table.Summary.Cell index={15} />}
            </Table.Summary.Row>
          </Table.Summary>
        )}
      />
    </Modal>
  )
}
