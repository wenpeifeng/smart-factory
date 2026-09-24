import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { App, Button, Form, Input, InputNumber, Modal, Select, Space, Table } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  fetchMaterialOptions,
  listEnabledFactories,
  listFactoryWarehouses,
  previewTransfer,
  fmtPrice,
  fmtQty,
  createTransfer,
  updateTransfer,
  type TransferDetail,
  type TransferLine,
  type TransferPayload,
} from '@/api/org'
import { useSubmitting } from '@/hooks/useSubmitting'
import { useDebounce } from '@/hooks/useDebounce'

/** 明细行编辑态（本地态承接用户输入，提交时组装） */
interface EditLine {
  key: number
  materialId?: number
  qty?: number
}

/** 头部表单值 */
interface HeadForm {
  fromFactoryId: number
  fromWarehouseId: number
  toFactoryId: number
  toWarehouseId: number
  remark?: string
}

interface EditModalProps {
  open: boolean
  /** 编辑对象（null=新建） */
  editing: TransferDetail | null
  onClose: () => void
  onSuccess: () => void
}

let lineKeySeq = 1

/**
 * 组织间调拨单 新建/编辑 弹窗。
 * <p>
 * 明细行的 成本单价/内部结算单价/结算金额 三列由后端「价格预演」接口实时计算
 * （成本=调出方最近加权价，结算=价目加价规则，无价目=成本），前端只读展示；
 * 保存提交 物料×数量，价格快照由服务端落库。
 */
export default function TransferEditModal({ open, editing, onClose, onSuccess }: EditModalProps) {
  const { message } = App.useApp()
  const [form] = Form.useForm<HeadForm>()
  const [lines, setLines] = useState<EditLine[]>([{ key: lineKeySeq++ }])
  const loadedIdRef = useRef<number | null>(null)

  const fromFactoryId = Form.useWatch('fromFactoryId', form)
  const toFactoryId = Form.useWatch('toFactoryId', form)
  const fromWarehouseId = Form.useWatch('fromWarehouseId', form)
  const toWarehouseId = Form.useWatch('toWarehouseId', form)

  // 打开时按编辑对象回显（同单重复打开不重复灌值）
  useEffect(() => {
    if (!open) return
    if (editing && loadedIdRef.current !== editing.id) {
      loadedIdRef.current = editing.id
      form.resetFields()
      form.setFieldsValue({
        fromFactoryId: editing.fromFactoryId,
        fromWarehouseId: editing.fromWarehouseId,
        toFactoryId: editing.toFactoryId,
        toWarehouseId: editing.toWarehouseId,
        remark: editing.remark ?? undefined,
      })
      setLines(
        editing.lines.map((l) => ({
          key: lineKeySeq++,
          materialId: l.materialId,
          qty: l.qty,
        })),
      )
    } else if (!editing) {
      loadedIdRef.current = null
      form.resetFields()
      setLines([{ key: lineKeySeq++ }])
    }
  }, [open, editing, form])

  const { data: factoryOptions } = useQuery({
    queryKey: ['org', 'factories', 'enabled'],
    queryFn: listEnabledFactories,
    enabled: open,
  })
  const { data: materialOptions } = useQuery({
    queryKey: ['basedata', 'materials', 'enabled'],
    queryFn: fetchMaterialOptions,
    enabled: open,
  })

  // 工厂下启用仓库（归属随工厂联动，仅启用仓可参与调拨）
  const { data: fromWarehouses } = useQuery({
    queryKey: ['org', 'factories', fromFactoryId, 'warehouses'],
    queryFn: () => listFactoryWarehouses(fromFactoryId!),
    enabled: open && !!fromFactoryId,
  })
  const { data: toWarehouses } = useQuery({
    queryKey: ['org', 'factories', toFactoryId, 'warehouses'],
    queryFn: () => listFactoryWarehouses(toFactoryId!),
    enabled: open && !!toFactoryId,
  })

  /** 完整可提交的行（预演与提交共用同一过滤口径，保证索引对齐） */
  const completeLines = useMemo(
    () => lines.filter((l): l is EditLine & { materialId: number; qty: number } =>
      !!l.materialId && !!l.qty && l.qty > 0),
    [lines],
  )

  const debouncedLines = useDebounce(
    useMemo(() => completeLines.map((l) => ({ materialId: l.materialId, qty: l.qty })), [completeLines]),
    350,
  )

  const routeReady = !!fromFactoryId && !!toFactoryId && !!fromWarehouseId && !!toWarehouseId

  // 价格预演：线路与明细齐备才请求；失败静默（拦截器已提示，价格列显示 -）
  const { data: previewLines, isFetching: previewLoading } = useQuery({
    queryKey: ['org', 'transfers', 'preview', fromFactoryId, toFactoryId, fromWarehouseId, toWarehouseId, debouncedLines],
    queryFn: () =>
      previewTransfer({
        fromFactoryId: fromFactoryId!,
        fromWarehouseId: fromWarehouseId!,
        toFactoryId: toFactoryId!,
        toWarehouseId: toWarehouseId!,
        lines: debouncedLines,
      }),
    enabled: open && routeReady && debouncedLines.length > 0,
    retry: false,
    placeholderData: (prev) => prev,
  })

  /** 第 idx 个「完整行」的价格（预演结果按完整行顺序对齐） */
  const priceOf = (completeIndex: number): TransferLine | undefined =>
    previewLines?.[completeIndex]

  const saveSubmit = useSubmitting(
    async () => {
      const values = await form.validateFields()
      if (completeLines.length === 0) {
        message.warning('请至少填写一行完整的调拨明细（物料 + 大于0的数量）')
        return
      }
      const payload: TransferPayload = {
        fromFactoryId: values.fromFactoryId,
        fromWarehouseId: values.fromWarehouseId,
        toFactoryId: values.toFactoryId,
        toWarehouseId: values.toWarehouseId,
        remark: values.remark,
        lines: completeLines.map((l) => ({ materialId: l.materialId, qty: l.qty })),
      }
      if (editing) await updateTransfer(editing.id, payload)
      else await createTransfer(payload)
    },
    {
      successText: editing ? '修改成功（草稿）' : '已保存草稿',
      onSuccess: onCloseAndDone,
    },
  )

  function onCloseAndDone() {
    onClose()
    onSuccess()
  }

  const updateLine = (key: number, patch: Partial<EditLine>) => {
    setLines((prev) => prev.map((l) => (l.key === key ? { ...l, ...patch } : l)))
  }

  const columns: ColumnsType<EditLine> = [
    {
      title: '物料',
      dataIndex: 'materialId',
      width: 260,
      render: (_, row) => (
        <Select
          placeholder="选择物料"
          showSearch
          optionFilterProp="label"
          style={{ width: '100%' }}
          value={row.materialId}
          options={(materialOptions ?? []).map((m) => ({
            label: `${m.code} ${m.name}`,
            value: m.id,
          }))}
          onChange={(v) => updateLine(row.key, { materialId: v })}
        />
      ),
    },
    {
      title: '数量',
      dataIndex: 'qty',
      width: 140,
      render: (_, row) => (
        <InputNumber
          placeholder=">0"
          min={0}
          style={{ width: '100%' }}
          value={row.qty}
          onChange={(v) => updateLine(row.key, { qty: v ?? undefined })}
        />
      ),
    },
    {
      title: '成本单价(自动)',
      key: 'costPrice',
      width: 130,
      render: (_, row) => {
        const idx = completeLines.findIndex((l) => l.key === row.key)
        return idx >= 0 ? fmtPrice(priceOf(idx)?.costPrice) : '-'
      },
    },
    {
      title: '结算单价(自动)',
      key: 'settlePrice',
      width: 130,
      render: (_, row) => {
        const idx = completeLines.findIndex((l) => l.key === row.key)
        return idx >= 0 ? fmtPrice(priceOf(idx)?.settlePrice) : '-'
      },
    },
    {
      title: '结算金额(自动)',
      key: 'settleAmount',
      width: 130,
      render: (_, row) => {
        const idx = completeLines.findIndex((l) => l.key === row.key)
        return idx >= 0 ? fmtPrice(priceOf(idx)?.settleAmount) : '-'
      },
    },
    {
      title: '',
      key: 'action',
      width: 60,
      render: (_, row) => (
        <Button
          type="link"
          size="small"
          danger
          disabled={lines.length <= 1}
          onClick={() => setLines((prev) => prev.filter((l) => l.key !== row.key))}
        >
          删除
        </Button>
      ),
    },
  ]

  const warehouseOptionOf = (list: { id: number; code: string; name: string; status: number }[] | undefined) =>
    (list ?? [])
      .filter((w) => w.status === 1)
      .map((w) => ({ label: `${w.code} ${w.name}`, value: w.id }))

  return (
    <Modal
      title={editing ? `修改调拨单 - ${editing.transferNo}` : '新建组织间调拨单'}
      open={open}
      onCancel={onClose}
      onOk={() => saveSubmit.run()}
      confirmLoading={saveSubmit.loading}
      destroyOnClose
      width={960}
    >
      <Form form={form} layout="vertical">
        <Space size={16} style={{ display: 'flex' }}>
          <Form.Item
            name="fromFactoryId"
            label="调出工厂"
            rules={[{ required: true, message: '请选择调出工厂' }]}
            style={{ width: 220 }}
          >
            <Select
              placeholder="调出工厂"
              showSearch
              optionFilterProp="label"
              options={(factoryOptions ?? []).map((f) => ({ label: `${f.code} ${f.name}`, value: f.id }))}
              onChange={() => form.setFieldValue('fromWarehouseId', undefined)}
            />
          </Form.Item>
          <Form.Item
            name="fromWarehouseId"
            label="调出仓库"
            rules={[{ required: true, message: '请选择调出仓库' }]}
            style={{ width: 220 }}
          >
            <Select
              placeholder="须归属调出工厂"
              showSearch
              optionFilterProp="label"
              options={warehouseOptionOf(fromWarehouses)}
              notFoundContent={fromFactoryId ? '该工厂下暂无启用仓库' : '请先选择调出工厂'}
            />
          </Form.Item>
          <Form.Item
            name="toFactoryId"
            label="调入工厂"
            rules={[{ required: true, message: '请选择调入工厂' }]}
            style={{ width: 220 }}
          >
            <Select
              placeholder="调入工厂（不能与调出相同）"
              showSearch
              optionFilterProp="label"
              options={(factoryOptions ?? []).map((f) => ({ label: `${f.code} ${f.name}`, value: f.id }))}
              onChange={() => form.setFieldValue('toWarehouseId', undefined)}
            />
          </Form.Item>
          <Form.Item
            name="toWarehouseId"
            label="调入仓库"
            rules={[{ required: true, message: '请选择调入仓库' }]}
            style={{ width: 220 }}
          >
            <Select
              placeholder="须归属调入工厂"
              showSearch
              optionFilterProp="label"
              options={warehouseOptionOf(toWarehouses)}
              notFoundContent={toFactoryId ? '该工厂下暂无启用仓库' : '请先选择调入工厂'}
            />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注" rules={[{ max: 200, message: '备注不能超过200字' }]}>
          <Input placeholder="备注" maxLength={200} />
        </Form.Item>
      </Form>

      <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'space-between' }}>
        <span style={{ color: '#8c939c', fontSize: 13 }}>
          成本/结算价由服务端自动计算：成本=调出方最近加权价；结算=价目加价（无价目=成本）
          {previewLoading ? '（计算中…）' : ''}
        </span>
        <Button size="small" onClick={() => setLines((prev) => [...prev, { key: lineKeySeq++ }])}>
          添加行
        </Button>
      </div>
      <Table<EditLine>
        rowKey="key"
        size="small"
        columns={columns}
        dataSource={lines}
        pagination={false}
        footer={() => {
          const totalQty = completeLines.reduce((s, l) => s + (l.qty ?? 0), 0)
          const totalAmount = previewLines?.reduce((s, p) => s + (p.settleAmount ?? 0), 0)
          return (
            <div style={{ textAlign: 'right' }}>
              数量合计 {fmtQty(totalQty)}
              {totalAmount != null && ` · 结算金额合计 ${fmtPrice(totalAmount)}`}
            </div>
          )
        }}
      />
    </Modal>
  )
}
