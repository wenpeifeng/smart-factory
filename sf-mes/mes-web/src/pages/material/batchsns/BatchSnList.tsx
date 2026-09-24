import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Form, Input, InputNumber, Modal, Select, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, PrinterOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import HasPerm from '@/components/HasPerm'
import { getBatchSns, printLabel, registerBatchSn, type BatchSn } from '@/api/material'
import { getMaterials } from '@/api/basedata'

const STATUS_LABEL: Record<string, string> = { IN_STOCK: '在库', CONSUMED: '已消耗', SHIPPED: '已出货' }

/** 批次/SN 档案页：登记 + 查询 + 标签打印留痕 */
export default function BatchSnList() {
  const queryClient = useQueryClient()
  const [form] = Form.useForm()
  const [keyword, setKeyword] = useState('')
  const [objType, setObjType] = useState<string | undefined>()
  const [modalOpen, setModalOpen] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ['material', 'batchsns', keyword, objType],
    queryFn: () => getBatchSns({ pageNum: 1, pageSize: 20, keyword: keyword || undefined, objType }),
  })
  const { data: materials } = useQuery({ queryKey: ['basedata', 'materials', 'for-sn'], queryFn: () => getMaterials({ pageNum: 1, pageSize: 200 }) })

  const registerMutation = useMutation({
    mutationFn: (values: Record<string, unknown>) =>
      registerBatchSn({
        objType: values.objType as string,
        objNo: values.objNo as string,
        materialId: values.materialId as number,
        qty: values.qty as number,
        sourceType: values.sourceType as string,
        sourceNo: values.sourceNo as string,
      }),
    onSuccess: () => {
      message.success('登记成功')
      setModalOpen(false)
      queryClient.invalidateQueries({ queryKey: ['material', 'batchsns'] })
    },
  })

  const printMutation = useMutation({
    mutationFn: (record: BatchSn) =>
      printLabel({ objType: record.objType, objId: record.id, objNo: record.objNo, template: 'default', printer: 'web' }),
    onSuccess: () => message.success('打印已登记（前端模板渲染窗口请随后接入）'),
  })

  const columns: ColumnsType<BatchSn> = [
    { title: '类型', dataIndex: 'objType', width: 80, render: (v: string) => (v === 'SN' ? 'SN' : '批次') },
    { title: '批次号/SN', dataIndex: 'objNo', width: 180 },
    { title: '物料', render: (_, r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}` },
    { title: '数量', dataIndex: 'qty', align: 'right' },
    { title: '来源单号', dataIndex: 'sourceNo', render: (v) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => <StatusTag value={STATUS_LABEL[v] ?? v} semantic={v === 'IN_STOCK' ? 'success' : 'default'} />,
    },
    {
      title: '操作',
      width: 90,
      render: (_, r) => (
        <HasPerm code="material:label:print">
          <Button size="small" type="link" icon={<PrinterOutlined />} onClick={() => printMutation.mutate(r)}>打印标签</Button>
        </HasPerm>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="批次 / SN 档案"
        subtitle="来料批次与产品 SN 的追溯档案（正反向追溯 P3 建设时消费）"
        extra={
          <HasPerm code="material:batchsn:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => { form.resetFields(); setModalOpen(true) }}>登记</Button>
          </HasPerm>
        }
      />
      <Table<BatchSn>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        title={() => (
          <Space>
            <Input.Search placeholder="批次号/SN" allowClear style={{ width: 220 }} onSearch={setKeyword} />
            <Select allowClear placeholder="类型" style={{ width: 100 }} onChange={setObjType} options={[{ value: 'BATCH', label: '批次' }, { value: 'SN', label: 'SN' }]} />
          </Space>
        )}
      />

      <Modal
        title="登记批次 / SN"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={registerMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(v) => registerMutation.mutate(v)}>
          <Form.Item name="objType" label="类型" initialValue="BATCH" rules={[{ required: true }]}>
            <Select options={[{ value: 'BATCH', label: '批次' }, { value: 'SN', label: 'SN' }]} />
          </Form.Item>
          <Form.Item name="objNo" label="批次号/SN（空=系统生成）">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="materialId" label="物料" rules={[{ required: true, message: '请选择物料' }]}>
            <Select showSearch optionFilterProp="label" options={(materials?.records ?? []).map((m) => ({ value: m.id, label: `${m.code} ${m.name}` }))} />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="qty" label="数量" initialValue={1} rules={[{ required: true }]}>
              <InputNumber min={0.0001} />
            </Form.Item>
            <Form.Item name="sourceType" label="来源类型" initialValue="PICKING">
              <Select style={{ width: 140 }} options={[{ value: 'PICKING', label: '领料' }, { value: 'PRODUCE', label: '生产产出' }, { value: 'PURCHASE', label: '采购到货' }]} />
            </Form.Item>
            <Form.Item name="sourceNo" label="来源单号">
              <Input />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </>
  )
}
