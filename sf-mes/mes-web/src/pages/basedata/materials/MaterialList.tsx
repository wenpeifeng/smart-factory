import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import HasPerm from '@/components/HasPerm'
import {
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
  Switch,
  Table,
  Tag,
  Tree,
  TreeSelect,
  Upload,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { DataNode } from 'antd/es/tree'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  FileAddOutlined,
  FolderAddOutlined,
  PlusOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import {
  changeMaterialStatus,
  createCategory,
  createMaterial,
  deleteCategory,
  deleteMaterial,
  downloadCsv,
  getCategoryTree,
  getEnabledUnits,
  getMaterials,
  importMaterials,
  updateCategory,
  updateMaterial,
  type Material,
  type MaterialCategory,
} from '@/api/basedata'
import PageHeader from '@/components/PageHeader'

/** 分类树选择节点（TreeSelect/Tree 共用结构） */
interface CategoryNode {
  title: string
  key: number
  value: number
  children: CategoryNode[]
}

/** 分类表单值 */
interface CategoryFormValues {
  parentId: number
  code: string
  name: string
  sort: number
  status: boolean
  remark?: string
}

/** 物料表单值 */
interface MaterialFormValues {
  code?: string
  name: string
  spec?: string
  categoryId?: number | null
  unitId?: number | null
  barcode?: string
  batchManaged: boolean
  serialManaged: boolean
  safetyStock?: number | null
  refCost?: number | null
  status: boolean
  remark?: string
}

/** 分类树 -> TreeSelect 数据（含「全部分类」根节点，parentId=0） */
function toTreeNodes(categories: MaterialCategory[]): CategoryNode[] {
  return categories.map((c) => ({
    title: c.name,
    key: c.id,
    value: c.id,
    children: toTreeNodes(c.children),
  }))
}

/** 物料列表页：左侧分类树 + 右侧物料表格（对标金蝶/用友物料管理布局） */
export default function MaterialList() {
  const queryClient = useQueryClient()
  const [materialForm] = Form.useForm<MaterialFormValues>()
  const [categoryForm] = Form.useForm<CategoryFormValues>()

  // 列表筛选状态（进入 queryKey，变化自动重新请求）
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [categoryId, setCategoryId] = useState<number | undefined>(undefined)
  const [status, setStatus] = useState<number | undefined>(undefined)

  // 弹窗状态
  const [materialOpen, setMaterialOpen] = useState(false)
  const [editingMaterial, setEditingMaterial] = useState<Material | null>(null)
  const [categoryOpen, setCategoryOpen] = useState(false)
  const [editingCategory, setEditingCategory] = useState<MaterialCategory | null>(null)

  // 基础数据：分类树 / 启用单位
  const { data: categoryTree = [] } = useQuery({
    queryKey: ['basedata', 'categories'],
    queryFn: getCategoryTree,
  })
  const { data: units = [] } = useQuery({
    queryKey: ['basedata', 'units', 'enabled'],
    queryFn: getEnabledUnits,
  })

  const query = {
    pageNum,
    pageSize,
    keyword: keyword || undefined,
    categoryId,
    status,
  }
  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['basedata', 'materials', query],
    queryFn: () => getMaterials(query),
  })

  // 树数据：追加「全部分类」根节点，key=-1 表示不过滤
  const treeData: DataNode[] = useMemo(
    () => [{ title: '全部分类', key: -1, children: toTreeNodes(categoryTree) }],
    [categoryTree],
  )
  const treeSelectData: CategoryNode[] = useMemo(
    () => [{ title: '(顶级分类)', key: 0, value: 0, children: toTreeNodes(categoryTree) }],
    [categoryTree],
  )

  // ===================== 物料 Mutation =====================
  const invalidateMaterials = () => queryClient.invalidateQueries({ queryKey: ['basedata', 'materials'] })

  const materialMutation = useMutation({
    mutationFn: async (values: MaterialFormValues) => {
      const payload = {
        code: values.code || undefined,
        name: values.name,
        spec: values.spec,
        categoryId: values.categoryId ?? null,
        unitId: values.unitId ?? null,
        barcode: values.barcode,
        batchManaged: values.batchManaged ? 1 : 0,
        serialManaged: values.serialManaged ? 1 : 0,
        safetyStock: values.safetyStock ?? 0,
        refCost: values.refCost ?? 0,
        status: values.status ? 1 : 0,
        remark: values.remark,
      }
      if (editingMaterial) {
        await updateMaterial(editingMaterial.id, payload)
      } else {
        await createMaterial(payload)
      }
    },
    onSuccess: () => {
      message.success(editingMaterial ? '修改成功' : '新增成功（编码留空已自动生成）')
      setMaterialOpen(false)
      invalidateMaterials()
    },
  })

  const statusMutation = useMutation({
    mutationFn: (m: Material) => changeMaterialStatus(m.id, m.status === 1 ? 0 : 1),
    onSuccess: () => {
      message.success('状态已更新')
      invalidateMaterials()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteMaterial(id),
    onSuccess: () => {
      message.success('删除成功')
      invalidateMaterials()
    },
  })

  // ===================== 分类 Mutation =====================
  const invalidateCategories = () => {
    queryClient.invalidateQueries({ queryKey: ['basedata', 'categories'] })
    invalidateMaterials()
  }

  const categoryMutation = useMutation({
    mutationFn: async (values: CategoryFormValues) => {
      const payload = {
        parentId: values.parentId,
        code: values.code,
        name: values.name,
        sort: values.sort,
        status: values.status ? 1 : 0,
        remark: values.remark,
      }
      if (editingCategory) {
        await updateCategory(editingCategory.id, payload)
      } else {
        await createCategory(payload)
      }
    },
    onSuccess: () => {
      message.success(editingCategory ? '修改成功' : '新增成功')
      setCategoryOpen(false)
      invalidateCategories()
    },
  })

  const categoryDeleteMutation = useMutation({
    mutationFn: (id: number) => deleteCategory(id),
    onSuccess: () => {
      message.success('删除成功')
      if (categoryId !== undefined) setCategoryId(undefined)
      invalidateCategories()
    },
  })

  // ===================== 弹窗打开逻辑 =====================
  const openCreateMaterial = () => {
    setEditingMaterial(null)
    materialForm.resetFields()
    setMaterialOpen(true)
  }

  const openEditMaterial = (record: Material) => {
    setEditingMaterial(record)
    materialForm.setFieldsValue({
      code: record.code,
      name: record.name,
      spec: record.spec ?? undefined,
      categoryId: record.categoryId ?? undefined,
      unitId: record.unitId ?? undefined,
      barcode: record.barcode ?? undefined,
      batchManaged: record.batchManaged === 1,
      serialManaged: record.serialManaged === 1,
      safetyStock: record.safetyStock,
      refCost: record.refCost,
      status: record.status === 1,
      remark: record.remark ?? undefined,
    })
    setMaterialOpen(true)
  }

  const openCreateCategory = (parentId: number) => {
    setEditingCategory(null)
    categoryForm.resetFields()
    categoryForm.setFieldsValue({ parentId, code: '', name: '', sort: 0, status: true })
    setCategoryOpen(true)
  }

  const openEditCategory = (node: MaterialCategory) => {
    setEditingCategory(node)
    categoryForm.setFieldsValue({
      parentId: node.parentId,
      code: node.code,
      name: node.name,
      sort: node.sort,
      status: node.status === 1,
      remark: node.remark ?? undefined,
    })
    setCategoryOpen(true)
  }

  /** 在树中递归查找分类（编辑入口用） */
  const findCategory = (nodes: MaterialCategory[], id: number): MaterialCategory | null => {
    for (const n of nodes) {
      if (n.id === id) return n
      const hit = findCategory(n.children, id)
      if (hit) return hit
    }
    return null
  }

  // ===================== CSV 导入 =====================
  const handleImport = async (file: File) => {
    const hide = message.loading('导入中...', 0)
    try {
      const result = await importMaterials(file)
      hide()
      Modal.info({
        title: `导入完成：成功 ${result.success} 条 / 失败 ${result.fail} 条`,
        content:
          result.errors.length > 0 ? (
            <ul style={{ maxHeight: 240, overflow: 'auto', paddingLeft: 18 }}>
              {result.errors.map((e) => (
                <li key={e}>{e}</li>
              ))}
            </ul>
          ) : (
            '全部导入成功'
          ),
      })
      invalidateMaterials()
    } catch {
      hide()
    }
  }

  // ===================== 列定义 =====================
  const columns: ColumnsType<Material> = [
    { title: '物料编码', dataIndex: 'code', width: 130 },
    { title: '物料名称', dataIndex: 'name', width: 160, ellipsis: true },
    { title: '规格型号', dataIndex: 'spec', width: 150, ellipsis: true },
    { title: '分类', dataIndex: 'categoryName', width: 100 },
    { title: '单位', dataIndex: 'unitName', width: 70 },
    { title: '条码', dataIndex: 'barcode', width: 120 },
    {
      title: '批次',
      dataIndex: 'batchManaged',
      width: 60,
      render: (v: number) => (v === 1 ? <Tag color="blue">批</Tag> : '-'),
    },
    {
      title: '序列号',
      dataIndex: 'serialManaged',
      width: 70,
      render: (v: number) => (v === 1 ? <Tag color="purple">序</Tag> : '-'),
    },
    { title: '安全库存', dataIndex: 'safetyStock', width: 90, align: 'right' },
    { title: '参考成本', dataIndex: 'refCost', width: 90, align: 'right' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: number) =>
        v === 1 ? <Tag color="success">启用</Tag> : <Tag color="default">停用</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 170,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <HasPerm code="basedata:material:edit">
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEditMaterial(record)}>
              编辑
            </Button>
          </HasPerm>
          <HasPerm code="basedata:material:edit">
            <Button
              type="link"
              size="small"
              onClick={() => statusMutation.mutate(record)}
              disabled={statusMutation.isPending}
            >
              {record.status === 1 ? '停用' : '启用'}
            </Button>
          </HasPerm>
          <HasPerm code="basedata:material:delete">
            <Popconfirm title="确认删除该物料？" onConfirm={() => deleteMutation.mutate(record.id)}>
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </HasPerm>
        </Space>
      ),
    },
  ]

  return (
    <div className="erp-page">
      <PageHeader title="物料管理" />

      <Row gutter={12}>
        {/* 左侧：物料分类树 */}
        <Col flex="240px">
          <Card
            size="small"
            title="物料分类"
            extra={
              <HasPerm code="basedata:material:add">
                <Button type="link" size="small" icon={<FolderAddOutlined />} onClick={() => openCreateCategory(categoryId && categoryId > 0 ? categoryId : 0)}>
                  新增
                </Button>
              </HasPerm>
            }
          >
            <Tree
              blockNode
              defaultExpandAll
              selectedKeys={[categoryId ?? -1]}
              treeData={treeData}
              onSelect={(keys) => {
                const key = keys[0]
                setCategoryId(typeof key === 'number' && key > 0 ? key : undefined)
                setPageNum(1)
              }}
            />
            {categoryId && categoryId > 0 && (
              <Space style={{ marginTop: 8 }}>
                <HasPerm code="basedata:material:edit">
                  <Button
                    size="small"
                    icon={<EditOutlined />}
                    onClick={() => {
                      const node = findCategory(categoryTree, categoryId)
                      if (node) openEditCategory(node)
                    }}
                  >
                    编辑
                  </Button>
                </HasPerm>
                <HasPerm code="basedata:material:delete">
                  <Popconfirm
                    title="确认删除该分类？"
                    description="被物料引用或存在子分类时将无法删除"
                    onConfirm={() => categoryDeleteMutation.mutate(categoryId)}
                  >
                    <Button size="small" danger icon={<DeleteOutlined />}>
                      删除
                    </Button>
                  </Popconfirm>
                </HasPerm>
              </Space>
            )}
          </Card>
        </Col>

        {/* 右侧：物料表格 */}
        <Col flex="auto">
          <Card size="small">
            <Space wrap style={{ marginBottom: 12 }}>
              <Input.Search
                placeholder="物料编码 / 名称"
                style={{ width: 220 }}
                allowClear
                onSearch={(v) => {
                  setKeyword(v)
                  setPageNum(1)
                }}
              />
              <Select
                placeholder="状态"
                style={{ width: 100 }}
                allowClear
                value={status}
                onChange={(v) => {
                  setStatus(v)
                  setPageNum(1)
                }}
                options={[
                  { value: 1, label: '启用' },
                  { value: 0, label: '停用' },
                ]}
              />
              <HasPerm code="basedata:material:add">
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreateMaterial}>
                  新增物料
                </Button>
              </HasPerm>
              <HasPerm code="basedata:material:add">
                <Upload accept=".csv" showUploadList={false} beforeUpload={(file) => { void handleImport(file); return false }}>
                  <Button icon={<UploadOutlined />}>导入CSV</Button>
                </Upload>
              </HasPerm>
              <HasPerm code="basedata:material:export">
                <Button
                  icon={<DownloadOutlined />}
                  onClick={() =>
                    downloadCsv(
                      `/basedata/materials/export?keyword=${encodeURIComponent(keyword)}${
                        categoryId ? `&categoryId=${categoryId}` : ''
                      }${status !== undefined ? `&status=${status}` : ''}`,
                      '物料导出.csv',
                    )
                  }
                >
                  导出
                </Button>
              </HasPerm>
              <HasPerm code="basedata:material:add">
                <Button
                  type="link"
                  icon={<FileAddOutlined />}
                  onClick={() => downloadCsv('/basedata/materials/import-template', '物料导入模板.csv')}
                >
                  下载模板
                </Button>
              </HasPerm>
            </Space>

            <Table<Material>
              rowKey="id"
              size="small"
              columns={columns}
              dataSource={data?.records ?? []}
              loading={isLoading || isFetching}
              scroll={{ x: 1420 }}
              pagination={{
                total: data?.total ?? 0,
                current: data?.pageNum ?? 1,
                pageSize: data?.pageSize ?? 10,
                showSizeChanger: true,
                showTotal: (t) => `共 ${t} 条`,
                onChange: (page, size) => {
                  setPageNum(page)
                  setPageSize(size)
                },
              }}
            />
          </Card>
        </Col>
      </Row>

      {/* 物料新增/修改弹窗 */}
      <Modal
        title={editingMaterial ? `编辑物料：${editingMaterial.code}` : '新增物料'}
        open={materialOpen}
        onCancel={() => setMaterialOpen(false)}
        onOk={() => materialForm.submit()}
        confirmLoading={materialMutation.isPending}
        width={680}
        destroyOnClose
      >
        <Form
          form={materialForm}
          layout="vertical"
          onFinish={(values) => materialMutation.mutate(values)}
          initialValues={{ batchManaged: false, serialManaged: false, status: true, safetyStock: 0, refCost: 0 }}
        >
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="code"
                label="物料编码"
                extra={editingMaterial ? undefined : '留空自动生成（WL+日期+流水）'}
              >
                <Input maxLength={40} placeholder="留空自动生成" disabled={!!editingMaterial} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="name" label="物料名称" rules={[{ required: true, message: '请输入物料名称' }]}>
                <Input maxLength={100} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="spec" label="规格型号">
                <Input maxLength={200} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="categoryId" label="物料分类">
                <TreeSelect
                  treeData={treeSelectData}
                  treeDefaultExpandAll
                  allowClear
                  placeholder="选择分类"
                  fieldNames={{ label: 'title', value: 'value', children: 'children' }}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="unitId" label="计量单位">
                <Select
                  allowClear
                  placeholder="选择单位"
                  options={units.map((u) => ({
                    value: u.id,
                    label: `${u.name}（${u.groupCode}组 ${u.rate}）`,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="barcode" label="条码">
                <Input maxLength={50} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="batchManaged" label="批次管理" valuePropName="checked">
                <Switch checkedChildren="是" unCheckedChildren="否" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="serialManaged" label="序列号管理" valuePropName="checked">
                <Switch checkedChildren="是" unCheckedChildren="否" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="status" label="状态" valuePropName="checked">
                <Switch checkedChildren="启用" unCheckedChildren="停用" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="safetyStock" label="安全库存">
                <InputNumber min={0} style={{ width: '100%' }} precision={4} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="refCost" label="参考成本价">
                <InputNumber min={0} style={{ width: '100%' }} precision={2} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="remark" label="备注">
                <Input.TextArea rows={2} maxLength={200} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      {/* 分类新增/修改弹窗 */}
      <Modal
        title={editingCategory ? `编辑分类：${editingCategory.name}` : '新增分类'}
        open={categoryOpen}
        onCancel={() => setCategoryOpen(false)}
        onOk={() => categoryForm.submit()}
        confirmLoading={categoryMutation.isPending}
        destroyOnClose
      >
        <Form
          form={categoryForm}
          layout="vertical"
          onFinish={(values) => categoryMutation.mutate(values)}
        >
          <Form.Item name="parentId" label="父分类" rules={[{ required: true }]}>
            <TreeSelect
              treeData={treeSelectData}
              treeDefaultExpandAll
              fieldNames={{ label: 'title', value: 'value', children: 'children' }}
              disabled={!!editingCategory}
            />
          </Form.Item>
          <Form.Item name="code" label="分类编码" rules={[{ required: true, message: '请输入分类编码' }]}>
            <Input maxLength={30} placeholder="如 CL / CP / BCP" />
          </Form.Item>
          <Form.Item name="name" label="分类名称" rules={[{ required: true, message: '请输入分类名称' }]}>
            <Input maxLength={50} />
          </Form.Item>
          <Form.Item name="sort" label="排序号" rules={[{ required: true }]}>
            <InputNumber min={0} max={9999} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="停用" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input maxLength={200} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
