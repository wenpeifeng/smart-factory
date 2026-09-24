import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  pageDictTypes,
  createDictType,
  updateDictType,
  deleteDictType,
  pageDictDatas,
  createDictData,
  updateDictData,
  deleteDictData,
  type SysDictType,
  type SysDictTypeForm,
  type SysDictData,
  type SysDictDataForm,
} from '@/api/system'

const { Title } = Typography

/** 字典类型 VO → 表单值 */
function toTypeForm(t: SysDictType): SysDictTypeForm {
  return { id: t.id, name: t.name, type: t.type, status: t.status, remark: t.remark ?? undefined }
}

/** 字典项 VO → 表单值 */
function toDataForm(d: SysDictData): SysDictDataForm {
  return {
    id: d.id,
    dictType: d.dictType,
    label: d.label,
    value: d.value,
    sort: d.sort,
    status: d.status,
    remark: d.remark ?? undefined,
  }
}

/**
 * 字典管理页：左右双栏。
 * 左侧字典类型（分页 CRUD），右侧选中类型的字典项（分页 CRUD）；
 * 编码变更由后端级联更新字典项关联。
 */
export default function DictPage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  // 左侧：类型
  const [typeKeyword, setTypeKeyword] = useState('')
  const [typePage, setTypePage] = useState(1)
  const [typeEditing, setTypeEditing] = useState<SysDictType | null>(null)
  const [typeOpen, setTypeOpen] = useState(false)
  const [typeForm] = Form.useForm<SysDictTypeForm>()

  // 右侧：字典项（跟随左侧选中类型）
  const [selectedType, setSelectedType] = useState<SysDictType | null>(null)
  const [dataPage, setDataPage] = useState(1)
  const [dataEditing, setDataEditing] = useState<SysDictData | null>(null)
  const [dataOpen, setDataOpen] = useState(false)
  const [dataForm] = Form.useForm<SysDictDataForm>()

  const { data: types, isLoading: typesLoading } = useQuery({
    queryKey: ['system', 'dictTypes', typeKeyword, typePage],
    queryFn: () => pageDictTypes({ pageNum: typePage, pageSize: 8, keyword: typeKeyword || undefined }),
  })

  const { data: datas, isLoading: datasLoading } = useQuery({
    queryKey: ['system', 'dictDatas', selectedType?.type, dataPage],
    queryFn: () =>
      pageDictDatas({ pageNum: dataPage, pageSize: 10, dictType: selectedType?.type }),
    enabled: !!selectedType,
  })

  const invalidateTypes = () => queryClient.invalidateQueries({ queryKey: ['system', 'dictTypes'] })
  const invalidateDatas = () => queryClient.invalidateQueries({ queryKey: ['system', 'dictDatas'] })

  const typeSave = useMutation({
    mutationFn: async (values: SysDictTypeForm) => {
      if (typeEditing) {
        await updateDictType(typeEditing.id, values)
      } else {
        await createDictType(values)
      }
    },
    onSuccess: () => {
      message.success('保存成功')
      setTypeOpen(false)
      invalidateTypes()
    },
  })
  const typeDelete = useMutation({
    mutationFn: deleteDictType,
    onSuccess: () => {
      message.success('删除成功')
      invalidateTypes()
    },
  })
  const dataSave = useMutation({
    mutationFn: async (values: SysDictDataForm) => {
      if (dataEditing) {
        await updateDictData(dataEditing.id, values)
      } else {
        await createDictData(values)
      }
    },
    onSuccess: () => {
      message.success('保存成功')
      setDataOpen(false)
      invalidateDatas()
    },
  })
  const dataDelete = useMutation({
    mutationFn: deleteDictData,
    onSuccess: () => {
      message.success('删除成功')
      invalidateDatas()
    },
  })

  const typeColumns: ColumnsType<SysDictType> = [
    { title: '字典名称', dataIndex: 'name', width: 110 },
    { title: '类型编码', dataIndex: 'type', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 70,
      render: (v: number) =>
        v === 1 ? <Tag color="success">启用</Tag> : <Tag color="default">停用</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 130,
      render: (_, record) => (
        <Space size={2}>
          <Button
            type="link"
            size="small"
            onClick={() => {
              if (selectedType?.id !== record.id) {
                setSelectedType(record)
                setDataPage(1)
              }
            }}
          >
            项
          </Button>
          <Button
            type="link"
            size="small"
            onClick={() => {
              setTypeEditing(record)
              typeForm.resetFields()
              typeForm.setFieldsValue(toTypeForm(record))
              setTypeOpen(true)
            }}
          >
            编辑
          </Button>
          <Popconfirm title="确认删除该类型？" onConfirm={() => typeDelete.mutate(record.id)}>
            <Button type="link" size="small" danger>
              删
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const dataColumns: ColumnsType<SysDictData> = [
    { title: '标签', dataIndex: 'label', width: 110 },
    { title: '值', dataIndex: 'value', width: 110 },
    { title: '排序', dataIndex: 'sort', width: 70 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 70,
      render: (v: number) =>
        v === 1 ? <Tag color="success">启用</Tag> : <Tag color="default">停用</Tag>,
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v) => v ?? '-' },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_, record) => (
        <Space size={2}>
          <Button
            type="link"
            size="small"
            onClick={() => {
              setDataEditing(record)
              dataForm.resetFields()
              dataForm.setFieldsValue(toDataForm(record))
              setDataOpen(true)
            }}
          >
            编辑
          </Button>
          <Popconfirm title="确认删除该字典项？" onConfirm={() => dataDelete.mutate(record.id)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        字典管理
      </Title>

      <Row gutter={16}>
        {/* 左：字典类型 */}
        <Col span={10}>
          <Card
            title="字典类型"
            size="small"
            extra={
              <Button
                type="primary"
                size="small"
                onClick={() => {
                  setTypeEditing(null)
                  typeForm.resetFields()
                  typeForm.setFieldsValue({ status: 1 })
                  setTypeOpen(true)
                }}
              >
                新增类型
              </Button>
            }
          >
            <Input.Search
              placeholder="名称 / 编码搜索"
              size="small"
              style={{ marginBottom: 12 }}
              allowClear
              onSearch={(v) => {
                setTypePage(1)
                setTypeKeyword(v)
              }}
            />
            <Table<SysDictType>
              rowKey="id"
              size="small"
              columns={typeColumns}
              dataSource={types?.records ?? []}
              loading={typesLoading}
              onRow={(record) => ({
                onClick: () => {
                  setSelectedType(record)
                  setDataPage(1)
                },
                style: { cursor: 'pointer', background: selectedType?.id === record.id ? '#e6f4ff' : undefined },
              })}
              pagination={{
                total: types?.total ?? 0,
                current: types?.pageNum ?? 1,
                pageSize: 8,
                size: 'small',
                onChange: setTypePage,
              }}
            />
          </Card>
        </Col>

        {/* 右：字典项 */}
        <Col span={14}>
          <Card
            title={`字典项${selectedType ? ` - ${selectedType.name}（${selectedType.type}）` : ''}`}
            size="small"
            extra={
              <Button
                type="primary"
                size="small"
                disabled={!selectedType}
                onClick={() => {
                  setDataEditing(null)
                  dataForm.resetFields()
                  dataForm.setFieldsValue({
                    dictType: selectedType?.type,
                    status: 1,
                    sort: (datas?.total ?? 0) + 1,
                  })
                  setDataOpen(true)
                }}
              >
                新增字典项
              </Button>
            }
          >
            {!selectedType ? (
              <div style={{ color: '#8c939c', padding: '40px 0', textAlign: 'center' }}>
                点击左侧字典类型查看字典项
              </div>
            ) : (
              <Table<SysDictData>
                rowKey="id"
                size="small"
                columns={dataColumns}
                dataSource={datas?.records ?? []}
                loading={datasLoading}
                pagination={{
                  total: datas?.total ?? 0,
                  current: datas?.pageNum ?? 1,
                  pageSize: 10,
                  size: 'small',
                  onChange: setDataPage,
                }}
              />
            )}
          </Card>
        </Col>
      </Row>

      {/* 类型弹窗 */}
      <Modal
        title={typeEditing ? `修改类型 - ${typeEditing.name}` : '新增字典类型'}
        open={typeOpen}
        onCancel={() => setTypeOpen(false)}
        onOk={() => typeForm.submit()}
        confirmLoading={typeSave.isPending}
        destroyOnClose
      >
        <Form form={typeForm} layout="vertical" onFinish={(values) => typeSave.mutate(values)}>
          <Form.Item name="name" label="字典名称" rules={[{ required: true, message: '请输入字典名称' }]}>
            <Input placeholder="如：通用状态" maxLength={50} />
          </Form.Item>
          <Form.Item
            name="type"
            label="类型编码"
            rules={[
              { required: true, message: '请输入类型编码' },
              { pattern: /^[A-Za-z][A-Za-z0-9_]{1,99}$/, message: '字母开头的字母/数字/下划线，2-100 位' },
            ]}
            extra="全局唯一；业务模块通过 GET /dict/{编码} 取选项"
          >
            <Input placeholder="如：sys_common_status" maxLength={100} />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue={1}>
            <Select
              style={{ width: 140 }}
              options={[
                { label: '启用', value: 1 },
                { label: '停用', value: 0 },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={200} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 字典项弹窗 */}
      <Modal
        title={dataEditing ? `修改字典项 - ${dataEditing.label}` : '新增字典项'}
        open={dataOpen}
        onCancel={() => setDataOpen(false)}
        onOk={() => dataForm.submit()}
        confirmLoading={dataSave.isPending}
        destroyOnClose
      >
        <Form form={dataForm} layout="vertical" onFinish={(values) => dataSave.mutate(values)}>
          <Form.Item name="dictType" label="所属类型编码" rules={[{ required: true }]}>
            <Input disabled />
          </Form.Item>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="label" label="标签" rules={[{ required: true, message: '请输入标签' }]} style={{ width: 200 }}>
              <Input placeholder="展示用，如：启用" maxLength={50} />
            </Form.Item>
            <Form.Item name="value" label="值" rules={[{ required: true, message: '请输入值' }]} style={{ width: 200 }}>
              <Input placeholder="存储用，如：1" maxLength={50} />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="sort" label="排序" initialValue={0}>
              <InputNumber min={0} max={9999} />
            </Form.Item>
            <Form.Item name="status" label="状态" initialValue={1}>
              <Select
                style={{ width: 140 }}
                options={[
                  { label: '启用', value: 1 },
                  { label: '停用', value: 0 },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={200} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
