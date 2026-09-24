import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Drawer, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import HasPerm from '@/components/HasPerm'
import {
  INSPECT_TYPE,
  changeTemplateStatus,
  createTemplate,
  deleteTemplate,
  getTemplateDetail,
  getTemplates,
  updateTemplate,
  type QcTemplate,
} from '@/api/quality'

interface ItemValues { itemName: string; standardValue?: string; lowerLimit?: number; upperLimit?: number; sampleRate?: number }

/** 检验模板页：IQC/IPQC/FQC 通用模板与项目行维护 */
export default function TemplateList() {
  const queryClient = useQueryClient()
  const [form] = Form.useForm()
  const [keyword, setKeyword] = useState('')
  const [inspectType, setInspectType] = useState<string | undefined>()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<QcTemplate | null>(null)
  const [detailId, setDetailId] = useState<number | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['quality', 'templates', keyword, inspectType],
    queryFn: () => getTemplates({ pageNum: 1, pageSize: 20, keyword: keyword || undefined, inspectType }),
  })
  const { data: detail } = useQuery({
    queryKey: ['quality', 'template', detailId],
    queryFn: () => getTemplateDetail(detailId!),
    enabled: detailId != null,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['quality', 'templates'] })

  const saveMutation = useMutation({
    mutationFn: async (values: Record<string, unknown>) => {
      const payload = {
        templateName: values.templateName as string,
        inspectType: values.inspectType as string,
        items: (values.items as ItemValues[]) ?? [],
      }
      if (editing) await updateTemplate(editing.id, payload)
      else await createTemplate(payload)
    },
    onSuccess: () => {
      message.success('保存成功')
      setModalOpen(false)
      invalidate()
    },
  })

  const statusMutation = useMutation({
    mutationFn: (t: QcTemplate) => changeTemplateStatus(t.id, t.status === 1 ? 0 : 1),
    onSuccess: () => {
      message.success('状态已更新')
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteTemplate(id),
    onSuccess: () => {
      message.success('删除成功')
      invalidate()
    },
  })

  const columns: ColumnsType<QcTemplate> = [
    { title: '模板编码', dataIndex: 'templateCode', width: 150 },
    { title: '模板名称', dataIndex: 'templateName' },
    { title: '检验类型', dataIndex: 'inspectType', width: 100, render: (v: string) => INSPECT_TYPE[v] ?? v },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number) => <StatusTag value={v === 1 ? '启用' : '停用'} semantic={v === 1 ? 'success' : 'default'} />,
    },
    {
      title: '操作',
      width: 240,
      render: (_, r) => (
        <Space size={4}>
          <Button size="small" type="link" onClick={() => setDetailId(r.id)}>详情</Button>
          <HasPerm code="quality:template:edit">
            <Button size="small" type="link" icon={<EditOutlined />} onClick={() => { setEditing(r); form.setFieldsValue({ templateName: r.templateName, inspectType: r.inspectType }); setModalOpen(true) }}>编辑</Button>
            <Button size="small" type="link" onClick={() => statusMutation.mutate(r)}>{r.status === 1 ? '停用' : '启用'}</Button>
          </HasPerm>
          <HasPerm code="quality:template:delete">
            <Popconfirm title="确认删除该模板？" onConfirm={() => deleteMutation.mutate(r.id)}>
              <Button size="small" type="link" danger icon={<DeleteOutlined />}>删除</Button>
            </Popconfirm>
          </HasPerm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="检验模板"
        subtitle="IQC 来料 / IPQC 过程 / FQC 成品检验模板与项目行（上下限自动判定）"
        extra={
          <HasPerm code="quality:template:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditing(null); form.resetFields(); setModalOpen(true) }}>新建模板</Button>
          </HasPerm>
        }
      />
      <Table<QcTemplate>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        title={() => (
          <Space>
            <Input.Search placeholder="编码/名称" allowClear style={{ width: 200 }} onSearch={setKeyword} />
            <Select allowClear placeholder="检验类型" style={{ width: 120 }} onChange={setInspectType} options={Object.entries(INSPECT_TYPE).map(([value, label]) => ({ value, label }))} />
          </Space>
        )}
      />

      <Modal
        title={editing ? '编辑模板' : '新建模板'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        width={680}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(v) => saveMutation.mutate(v)}>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="templateName" label="模板名称" rules={[{ required: true, message: '请输入名称' }]} style={{ width: 280 }}>
              <Input maxLength={100} />
            </Form.Item>
            <Form.Item name="inspectType" label="检验类型" rules={[{ required: true }]} initialValue="IQC">
              <Select style={{ width: 140 }} options={Object.entries(INSPECT_TYPE).map(([value, label]) => ({ value, label }))} />
            </Form.Item>
          </Space>
          <Form.Item label="检验项目行" required style={{ marginBottom: 0 }}>
            <Form.List name="items" initialValue={[{}]}>
              {(fields, { add, remove }) => (
                <>
                  {fields.map((field) => (
                    <Space key={field.key} style={{ display: 'flex', marginBottom: 4 }} align="baseline">
                      <Form.Item name={[field.name, 'itemName']} rules={[{ required: true, message: '项目名' }]} noStyle>
                        <Input placeholder="项目名称" style={{ width: 140 }} />
                      </Form.Item>
                      <Form.Item name={[field.name, 'standardValue']} noStyle>
                        <Input placeholder="标准值 如 10±0.1" style={{ width: 130 }} />
                      </Form.Item>
                      <Form.Item name={[field.name, 'lowerLimit']} noStyle>
                        <InputNumber placeholder="下限" style={{ width: 90 }} />
                      </Form.Item>
                      <Form.Item name={[field.name, 'upperLimit']} noStyle>
                        <InputNumber placeholder="上限" style={{ width: 90 }} />
                      </Form.Item>
                      <Button type="link" danger onClick={() => remove(field.name)}>删除</Button>
                    </Space>
                  ))}
                  <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({})}>添加项目</Button>
                </>
              )}
            </Form.List>
          </Form.Item>
        </Form>
      </Modal>

      <Drawer title={`模板：${detail?.templateName ?? ''}`} width={640} open={detailId != null} onClose={() => setDetailId(null)}>
        <Table
          rowKey="id"
          size="small"
          loading={detailId != null && !detail}
          pagination={false}
          dataSource={detail?.items ?? []}
          columns={[
            { title: '项目', dataIndex: 'itemName' },
            { title: '标准值', dataIndex: 'standardValue', render: (v) => v ?? '-' },
            { title: '下限', dataIndex: 'lowerLimit', render: (v) => v ?? '-' },
            { title: '上限', dataIndex: 'upperLimit', render: (v) => v ?? '-' },
          ]}
        />
      </Drawer>
    </>
  )
}
