import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  TreeSelect,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  templateApi,
  type QcTemplateDetail,
  type QcTemplateRow,
  type QcTemplateSavePayload,
  type QcTemplateItemInput,
  type QcTemplatePageParams,
} from '@/api/qc'
import { InspectTypeTag, INSPECT_TYPE_FILTER, categoryToTreeData, useCategoryTree } from '../shared'

const { Title } = Typography

/**
 * 检验模板管理页：模板列表 + 新增/修改弹窗（检验项目子表 Form.List）。
 * 模板 = 检验类型 + 适用物料分类 + 检验项目集合；检验单明细为模板快照。
 */
export default function TemplateList() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [params, setParams] = useState<QcTemplatePageParams>({ pageNum: 1, pageSize: 10 })
  const [editing, setEditing] = useState<QcTemplateDetail | null>(null)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<QcTemplateSavePayload>()
  // 子表数组用 useWatch 监听，增删行时整表重渲染
  const watchedItems = Form.useWatch('items', form) as QcTemplateItemInput[] | undefined

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['qc', 'templates', params],
    queryFn: () => templateApi.page(params),
  })
  const { data: categoryTree } = useCategoryTree()

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['qc', 'templates'] })

  const saveMutation = useMutation({
    mutationFn: async (values: QcTemplateSavePayload) => {
      if (editing) {
        await templateApi.update(editing.id, values)
      } else {
        await templateApi.create(values)
      }
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '新增成功')
      setOpen(false)
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: templateApi.remove,
    onSuccess: () => {
      message.success('删除成功')
      invalidate()
    },
  })

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      status: 1,
      inspectType: 'IQC',
      items: [{ sampleRate: 100, seq: 1 }],
    })
    setOpen(true)
  }

  const openEdit = async (row: QcTemplateRow) => {
    const detail = await templateApi.get(row.id)
    setEditing(detail)
    form.resetFields()
    form.setFieldsValue({
      templateCode: detail.templateCode,
      templateName: detail.templateName,
      categoryId: detail.categoryId ?? undefined,
      inspectType: detail.inspectType,
      status: detail.status,
      remark: detail.remark ?? undefined,
      items: detail.items.map((it) => ({
        itemName: it.itemName,
        standardValue: it.standardValue ?? undefined,
        lowerLimit: it.lowerLimit ?? undefined,
        upperLimit: it.upperLimit ?? undefined,
        inspectMethod: it.inspectMethod ?? undefined,
        sampleRate: it.sampleRate,
        seq: it.seq,
        remark: it.remark ?? undefined,
      })),
    })
    setOpen(true)
  }

  const columns: ColumnsType<QcTemplateRow> = [
    { title: 'ID', dataIndex: 'id', width: 64 },
    { title: '模板编码', dataIndex: 'templateCode', width: 140 },
    { title: '模板名称', dataIndex: 'templateName', width: 180, ellipsis: true },
    {
      title: '适用分类',
      dataIndex: 'categoryName',
      width: 130,
      render: (v: string | null) => v ?? <Tag>全部分类</Tag>,
    },
    { title: '检验类型', dataIndex: 'inspectType', width: 110, render: (t) => <InspectTypeTag type={t} /> },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: 0 | 1) =>
        v === 1 ? <Tag color="success">启用</Tag> : <Tag>停用</Tag>,
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v) => v ?? '-' },
    { title: '创建时间', dataIndex: 'createTime', width: 165 },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, record) => (
        <Space size={4}>
          <HasPerm code="qc:template:edit">
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
          </HasPerm>
          <HasPerm code="qc:template:delete">
            <Popconfirm
              title="确认删除该模板？"
              description="已生成检验单的明细为快照，不受影响"
              onConfirm={() => deleteMutation.mutate(record.id)}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          </HasPerm>
        </Space>
      ),
    },
  ]

  /** 项目子表列（弹窗内 Form.List 渲染） */
  const itemColumns: ColumnsType<QcTemplateItemInput> = [
    {
      title: '检验项目',
      dataIndex: 'itemName',
      render: (_, __, idx) => (
        <Form.Item
          name={['items', idx, 'itemName']}
          rules={[{ required: true, message: '必填' }]}
          style={{ marginBottom: 0 }}
        >
          <Input placeholder="如 外观/尺寸" maxLength={100} />
        </Form.Item>
      ),
    },
    {
      title: '标准值',
      dataIndex: 'standardValue',
      width: 130,
      render: (_, __, idx) => (
        <Form.Item name={['items', idx, 'standardValue']} style={{ marginBottom: 0 }}>
          <Input placeholder="如 10.00±0.10" maxLength={100} />
        </Form.Item>
      ),
    },
    {
      title: '下限',
      dataIndex: 'lowerLimit',
      width: 110,
      render: (_, __, idx) => (
        <Form.Item name={['items', idx, 'lowerLimit']} style={{ marginBottom: 0 }}>
          <InputNumber placeholder="不限" min={0} style={{ width: '100%' }} />
        </Form.Item>
      ),
    },
    {
      title: '上限',
      dataIndex: 'upperLimit',
      width: 110,
      render: (_, __, idx) => (
        <Form.Item name={['items', idx, 'upperLimit']} style={{ marginBottom: 0 }}>
          <InputNumber placeholder="不限" min={0} style={{ width: '100%' }} />
        </Form.Item>
      ),
    },
    {
      title: '检验方法',
      dataIndex: 'inspectMethod',
      width: 130,
      render: (_, __, idx) => (
        <Form.Item name={['items', idx, 'inspectMethod']} style={{ marginBottom: 0 }}>
          <Input placeholder="如 目测" maxLength={100} />
        </Form.Item>
      ),
    },
    {
      title: '抽检比例%',
      dataIndex: 'sampleRate',
      width: 110,
      render: (_, __, idx) => (
        <Form.Item
          name={['items', idx, 'sampleRate']}
          rules={[{ required: true, message: '必填' }]}
          style={{ marginBottom: 0 }}
        >
          <InputNumber min={0} max={100} style={{ width: '100%' }} />
        </Form.Item>
      ),
    },
    {
      title: '排序',
      dataIndex: 'seq',
      width: 80,
      render: (_, __, idx) => (
        <Form.Item name={['items', idx, 'seq']} style={{ marginBottom: 0 }}>
          <InputNumber min={0} precision={0} style={{ width: '100%' }} />
        </Form.Item>
      ),
    },
    {
      title: '操作',
      key: 'op',
      width: 70,
      render: (_, __, idx) => (
        <Button
          type="link"
          size="small"
          danger
          onClick={() => form.setFieldValue('items', removeItemAt(form.getFieldValue('items'), idx))}
        >
          删除
        </Button>
      ),
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        检验模板
      </Title>

      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <Input.Search
            placeholder="模板编码 / 名称模糊搜索"
            style={{ width: 240 }}
            allowClear
            onSearch={(v) => setParams((p) => ({ ...p, pageNum: 1, keyword: v || undefined }))}
          />
          <Select
            placeholder="检验类型"
            style={{ width: 150 }}
            allowClear
            options={INSPECT_TYPE_FILTER}
            onChange={(v) => setParams((p) => ({ ...p, pageNum: 1, inspectType: v }))}
          />
          <Select
            placeholder="状态"
            style={{ width: 110 }}
            allowClear
            options={[
              { value: 1, label: '启用' },
              { value: 0, label: '停用' },
            ]}
            onChange={(v) => setParams((p) => ({ ...p, pageNum: 1, status: v }))}
          />
          <HasPerm code="qc:template:add">
          <Button type="primary" onClick={openCreate}>
            新增模板
          </Button>
          </HasPerm>
        </Space>

        <Table<QcTemplateRow>
          rowKey="id"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          pagination={{
            total: data?.total ?? 0,
            current: data?.pageNum ?? params.pageNum,
            pageSize: data?.pageSize ?? params.pageSize,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => setParams({ pageNum: p, pageSize: s }),
          }}
        />
      </Card>

      <Modal
        title={editing ? `修改模板 - ${editing.templateCode}` : '新增检验模板'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        destroyOnClose
        width={1080}
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Space size={16} style={{ display: 'flex' }} wrap>
            <Form.Item
              name="templateCode"
              label="模板编码"
              rules={[
                { required: true, message: '请输入模板编码' },
                { max: 40, message: '最长 40 字' },
              ]}
              style={{ width: 220 }}
            >
              <Input placeholder="如 QT0001" maxLength={40} />
            </Form.Item>
            <Form.Item
              name="templateName"
              label="模板名称"
              rules={[{ required: true, message: '请输入模板名称' }]}
              style={{ width: 260 }}
            >
              <Input placeholder="如 电子元器件来料检验" maxLength={100} />
            </Form.Item>
            <Form.Item
              name="inspectType"
              label="检验类型"
              rules={[{ required: true, message: '请选择检验类型' }]}
              style={{ width: 180 }}
            >
              <Select options={INSPECT_TYPE_FILTER} />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }} wrap>
            <Form.Item name="categoryId" label="适用物料分类" style={{ width: 300 }}>
              <TreeSelect
                placeholder="不选 = 全部分类通用"
                allowClear
                treeDefaultExpandAll
                treeData={categoryToTreeData(categoryTree ?? [])}
              />
            </Form.Item>
            <Form.Item name="status" label="状态" initialValue={1} style={{ width: 140 }}>
              <Select
                options={[
                  { value: 1, label: '启用' },
                  { value: 0, label: '停用' },
                ]}
              />
            </Form.Item>
            <Form.Item name="remark" label="备注" style={{ width: 380 }}>
              <Input placeholder="备注" maxLength={500} />
            </Form.Item>
          </Space>

          <Form.Item label="检验项目（至少一行；生成检验单时快照带出）" required style={{ marginBottom: 8 }}>
            <Table<QcTemplateItemInput>
              rowKey={(_, idx) => String(idx)}
              size="small"
              columns={itemColumns}
              dataSource={watchedItems ?? []}
              pagination={false}
              footer={() => (
                <Button
                  type="dashed"
                  block
                  onClick={() =>
                    form.setFieldValue('items', [
                      ...(watchedItems ?? []),
                      { sampleRate: 100, seq: (watchedItems?.length ?? 0) + 1 },
                    ])
                  }
                >
                  + 添加检验项目
                </Button>
              )}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

/** 从数组移除第 idx 项（整表 setFieldValue，触发 useWatch 重渲染） */
function removeItemAt<T>(items: T[] | undefined, idx: number): T[] {
  const list = [...(items ?? [])]
  list.splice(idx, 1)
  return list
}
