import { useEffect, useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import PageTable from '@/components/PageTable'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import { useDebounce } from '@/hooks/useDebounce'
import { useSubmitting } from '@/hooks/useSubmitting'
import {
  factoryStockStats,
  assignWarehouse,
  createFactory,
  deleteFactory,
  listEnabledFactories,
  listFactoryWarehouses,
  updateFactory,
  updateFactoryStatus,
  fetchWarehouseOptions,
  type Factory,
  type FactoryPayload,
  type FactoryStockRow,
  type WarehouseBelong,
} from '@/api/org'

const { Text } = Typography

/** 工厂仓库弹窗：工厂下仓库清单 + 各仓库库存量汇总（打开才加载） */
function FactoryWarehouseModal({
  factory,
  onClose,
}: {
  factory: Factory | null
  onClose: () => void
}) {
  const factoryId = factory?.id
  const { data: warehouses, isLoading } = useQuery({
    queryKey: ['org', 'factories', factoryId, 'warehouses'],
    queryFn: () => listFactoryWarehouses(factoryId!),
    enabled: !!factoryId,
  })
  const { data: stats } = useQuery({
    queryKey: ['org', 'statistics', 'stock', factoryId],
    queryFn: () => factoryStockStats(factoryId!),
    enabled: !!factoryId,
  })

  // 仓库清单与库存统计按仓库 ID 客户端拼装（两个只读接口同源）
  const statMap = useMemo(() => new Map((stats ?? []).map((s) => [s.warehouseId, s])), [stats])

  const columns: ColumnsType<WarehouseBelong> = [
    { title: '仓库编码', dataIndex: 'code', width: 130 },
    { title: '仓库名称', dataIndex: 'name', width: 150 },
    { title: '管理员', dataIndex: 'manager', width: 100, render: (v) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: number) => <StatusTag value={v === 1 ? '启用' : '停用'} />,
    },
    {
      title: '物料种数',
      key: 'materialCount',
      width: 100,
      render: (_, row) => statMap.get(row.id)?.materialCount ?? 0,
    },
    {
      title: '库存量合计',
      key: 'totalQty',
      width: 130,
      render: (_, row) => {
        const s: FactoryStockRow | undefined = statMap.get(row.id)
        return (
          <Text strong={!!s && s.totalQty > 0}>{s ? s.totalQty.toLocaleString('zh-CN') : '0'}</Text>
        )
      },
    },
  ]

  return (
    <Modal
      title={`工厂下仓库 - ${factory?.code ?? ''} ${factory?.name ?? ''}`}
      open={!!factory}
      onCancel={onClose}
      footer={null}
      destroyOnClose
      width={720}
    >
      <Table<WarehouseBelong>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={warehouses ?? []}
        loading={isLoading}
        pagination={false}
        locale={{ emptyText: '该工厂下暂无归属仓库' }}
      />
    </Modal>
  )
}

/**
 * 工厂档案页：分页查询 / 新增修改 / 启停 / 删除 / 查看工厂下仓库与库存 / 仓库归属调整。
 * 服务端态走 TanStack Query（PageTable 承载主列表）；写操作 useSubmitting 防重。
 */
export default function FactoryList() {
  const queryClient = useQueryClient()

  const [name, setName] = useState('')
  const [status, setStatus] = useState<number | undefined>()
  const debouncedName = useDebounce(name)

  // 新增/修改弹窗
  const [editing, setEditing] = useState<Factory | null>(null)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<FactoryPayload>()

  // 仓库归属调整弹窗
  const [assignOpen, setAssignOpen] = useState(false)
  const [assignForm] = Form.useForm<{ warehouseId: number; factoryId: number }>()

  // 查看仓库弹窗目标工厂
  const [whTarget, setWhTarget] = useState<Factory | null>(null)

  const { data: factoryOptions } = useQuery({
    queryKey: ['org', 'factories', 'enabled'],
    queryFn: listEnabledFactories,
  })
  const { data: warehouseOptions } = useQuery({
    queryKey: ['basedata', 'warehouses', 'enabled'],
    queryFn: fetchWarehouseOptions,
  })

  useEffect(() => {
    if (assignOpen) assignForm.resetFields()
  }, [assignOpen, assignForm])

  // 工厂/归属变化会影响列表仓库数、展开面板与统计，统一失效 org 域缓存
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['org'] })

  const saveSubmit = useSubmitting(
    async (values: FactoryPayload) => {
      if (editing) await updateFactory(editing.id, values)
      else await createFactory(values)
    },
    {
      successText: editing ? '修改成功' : '新增成功',
      onSuccess: () => {
        setOpen(false)
        invalidate()
      },
    },
  )

  const statusSubmit = useSubmitting(
    (row: Factory) => updateFactoryStatus(row.id, row.status === 1 ? 0 : 1),
    { successText: '状态已更新', onSuccess: invalidate },
  )

  const deleteSubmit = useSubmitting((id: number) => deleteFactory(id), {
    successText: '删除成功',
    onSuccess: invalidate,
  })

  const assignSubmit = useSubmitting(
    async (values: { warehouseId: number; factoryId: number }) => {
      await assignWarehouse(values.warehouseId, values.factoryId)
    },
    {
      successText: '仓库归属已调整',
      onSuccess: () => {
        setAssignOpen(false)
        invalidate()
      },
    },
  )

  const columns: ColumnsType<Factory> = [
    { title: 'ID', dataIndex: 'id', width: 64 },
    { title: '工厂编码', dataIndex: 'code', width: 140 },
    { title: '工厂名称', dataIndex: 'name', width: 150 },
    { title: '地址', dataIndex: 'address', ellipsis: true, render: (v) => v ?? '-' },
    { title: '负责人', dataIndex: 'manager', width: 100, render: (v) => v ?? '-' },
    { title: '仓库数', dataIndex: 'warehouseCount', width: 90, align: 'right' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (_, record) => (
        <Switch
          checked={record.status === 1}
          checkedChildren="启用"
          unCheckedChildren="停用"
          disabled={record.id === 1}
          loading={statusSubmit.loading}
          onChange={() => statusSubmit.run(record)}
        />
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => setWhTarget(record)}>
            查看仓库
          </Button>
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditing(record)
              form.resetFields()
              form.setFieldsValue({
                name: record.name,
                address: record.address ?? undefined,
                manager: record.manager ?? undefined,
                status: record.status,
                remark: record.remark ?? undefined,
              })
              setOpen(true)
            }}
          >
            编辑
          </Button>
          {record.id !== 1 && (
            <Popconfirm title="确认删除该工厂？" onConfirm={() => deleteSubmit.run(record.id)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className="erp-page">
      <PageHeader
        title="工厂档案"
        subtitle="轻量多组织：多工厂档案、仓库归属与工厂库存汇总；点击「查看仓库」看工厂下仓库与库存"
        extra={
          <>
            <Button onClick={() => setAssignOpen(true)}>仓库归属调整</Button>
            <Button
              type="primary"
              onClick={() => {
                setEditing(null)
                form.resetFields()
                form.setFieldsValue({ status: 1 })
                setOpen(true)
              }}
            >
              新增工厂
            </Button>
          </>
        }
      />

      <PageTable<Factory>
        url="/org/factories"
        queryKey={['org', 'factories', debouncedName, status]}
        params={{ name: debouncedName || undefined, status }}
        columns={columns}
        toolbar={
          <>
            <Input.Search
              placeholder="工厂名称 / 编码搜索"
              style={{ width: 220 }}
              allowClear
              onSearch={(v) => setName(v)}
            />
            <Select
              placeholder="状态"
              style={{ width: 120 }}
              allowClear
              options={[
                { label: '启用', value: 1 },
                { label: '停用', value: 0 },
              ]}
              value={status}
              onChange={(v) => setStatus(v)}
            />
          </>
        }
      />

      <FactoryWarehouseModal factory={whTarget} onClose={() => setWhTarget(null)} />

      {/* 新增 / 修改弹窗 */}
      <Modal
        title={editing ? `修改工厂 - ${editing.code}` : '新增工厂'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveSubmit.loading}
        destroyOnClose
        width={520}
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveSubmit.run(values)}>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="name"
              label="工厂名称"
              rules={[
                { required: true, message: '请输入工厂名称' },
                { max: 100, message: '名称不能超过100字' },
              ]}
              style={{ width: 220 }}
            >
              <Input placeholder="如：苏州工厂" maxLength={100} />
            </Form.Item>
            <Form.Item name="manager" label="负责人" style={{ width: 220 }}>
              <Input placeholder="负责人" maxLength={50} />
            </Form.Item>
          </Space>
          <Form.Item name="address" label="地址" rules={[{ max: 200, message: '地址不能超过200字' }]}>
            <Input placeholder="工厂地址" maxLength={200} />
          </Form.Item>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="status" label="状态" initialValue={1} style={{ width: 220 }}>
              <Select
                options={[
                  { label: '启用', value: 1 },
                  { label: '停用', value: 0 },
                ]}
              />
            </Form.Item>
            <Form.Item
              name="code"
              label="工厂编码"
              style={{ width: 220 }}
              extra={editing ? '编码不可修改' : '留空自动生成 FC+日期+流水'}
            >
              <Input placeholder="留空自动生成" maxLength={40} disabled={!!editing} />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea placeholder="备注" maxLength={200} rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 仓库归属调整弹窗 */}
      <Modal
        title="仓库归属调整"
        open={assignOpen}
        onCancel={() => setAssignOpen(false)}
        onOk={() => assignForm.validateFields().then((v) => assignSubmit.run(v))}
        confirmLoading={assignSubmit.loading}
        destroyOnClose
      >
        <Form form={assignForm} layout="vertical">
          <Form.Item name="warehouseId" label="仓库" rules={[{ required: true, message: '请选择仓库' }]}>
            <Select
              placeholder="选择仓库"
              showSearch
              optionFilterProp="label"
              options={(warehouseOptions ?? []).map((w) => ({
                label: `${w.code} ${w.name}`,
                value: w.id,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="factoryId"
            label="调整到工厂"
            rules={[{ required: true, message: '请选择目标工厂' }]}
          >
            <Select
              placeholder="选择目标工厂（须为启用状态）"
              showSearch
              optionFilterProp="label"
              options={(factoryOptions ?? []).map((f) => ({
                label: `${f.code} ${f.name}`,
                value: f.id,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
