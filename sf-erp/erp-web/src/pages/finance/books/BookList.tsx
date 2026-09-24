import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  copyBookSubjects,
  createBook,
  deleteBook,
  getBooks,
  getFinanceBookId,
  setFinanceBookId,
  updateBook,
  type BookVO,
} from '@/api/finance'
import BookSelect from '@/pages/finance/components/BookSelect'

const { Title, Text } = Typography

/** 账套编辑表单值 */
interface BookFormValues {
  name: string
  periodPolicy: string
  currency: string
  status: number
  remark?: string
}

/**
 * 账套管理页（对标金蝶多账套）：
 * 账套列表 + 新增/编辑/删除（默认账套与已有凭证的账套后端禁删）+
 * 新账套「复制科目」初始化 + 当前账套徽标（页面内切换，与财务各页筛选栏一致）。
 */
export default function BookList() {
  const queryClient = useQueryClient()
  const [currentBook, setCurrentBook] = useState<number>(getFinanceBookId())
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<BookVO | null>(null)
  const [form] = Form.useForm<BookFormValues>()

  const { data: books, isLoading, isFetching } = useQuery({
    queryKey: ['finance', 'books'],
    queryFn: getBooks,
  })

  // 打开弹窗时回填（编辑）或重置（新增）
  useEffect(() => {
    if (!modalOpen) return
    if (editing) {
      form.setFieldsValue({
        name: editing.name,
        periodPolicy: editing.periodPolicy,
        currency: editing.currency,
        status: editing.status,
        remark: editing.remark ?? undefined,
      })
    } else {
      form.resetFields()
    }
  }, [modalOpen, editing, form])

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['finance', 'books'] })

  const saveMutation = useMutation({
    mutationFn: async (values: BookFormValues) => {
      const body = {
        name: values.name.trim(),
        periodPolicy: values.periodPolicy,
        currency: values.currency,
        status: values.status,
        remark: values.remark?.trim() || undefined,
      }
      if (editing) {
        await updateBook(editing.id, body)
      } else {
        await createBook(body)
      }
    },
    onSuccess: () => {
      message.success(editing ? '账套修改成功' : '账套创建成功')
      invalidate()
      setModalOpen(false)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteBook,
    onSuccess: () => {
      message.success('账套已删除')
      invalidate()
    },
  })

  /** 复制主账套科目树到目标账套（幂等：已有同编码科目跳过） */
  const copyMutation = useMutation({
    mutationFn: (toBookId: number) => copyBookSubjects(toBookId, 1001),
    onSuccess: (count) => {
      message.success(`科目复制完成：本次新增 ${count} 条`)
      queryClient.invalidateQueries({ queryKey: ['finance', 'subjects'] })
    },
  })

  /** 切换当前账套：更新模块级上下文 + 失效全部财务查询缓存 */
  const switchBook = (bookId: number) => {
    setFinanceBookId(bookId)
    setCurrentBook(bookId)
    queryClient.invalidateQueries({ queryKey: ['finance'] })
    message.info(`已切换到账套 ${bookId}`)
  }

  const columns: ColumnsType<BookVO> = [
    { title: '账套 ID', dataIndex: 'id', width: 90 },
    { title: '编码', dataIndex: 'code', width: 110 },
    { title: '名称', dataIndex: 'name', width: 160 },
    {
      title: '当前',
      key: 'current',
      width: 80,
      align: 'center',
      render: (_, row) =>
        row.id === currentBook ? <Tag color="processing">当前</Tag> : null,
    },
    {
      title: '会计期间',
      dataIndex: 'periodPolicy',
      width: 110,
      render: (v: string) => (v === 'NATURAL_MONTH' ? '自然月' : v),
    },
    { title: '本位币', dataIndex: 'currency', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number) =>
        v === 1 ? <Tag color="success">启用</Tag> : <Tag color="default">停用</Tag>,
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
      key: 'action',
      width: 240,
      render: (_, row) => (
        <Space size={0}>
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditing(row)
              setModalOpen(true)
            }}
          >
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            loading={copyMutation.isPending && copyMutation.variables === row.id}
            onClick={() => copyMutation.mutate(row.id)}
          >
            复制科目
          </Button>
          {row.isDefault === 1 ? (
            <Text type="secondary" style={{ fontSize: 12, paddingLeft: 8 }}>
              默认账套不可删
            </Text>
          ) : (
            <Popconfirm
              title="确认删除该账套？"
              description="已有凭证的账套将被拒绝删除"
              onConfirm={() => deleteMutation.mutate(row.id)}
            >
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
      <Title level={4} style={{ marginTop: 0 }}>
        账套管理
      </Title>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <span style={{ color: '#666' }}>当前账套：</span>
          <BookSelect value={currentBook} onChange={switchBook} />
          <Button
            type="primary"
            onClick={() => {
              setEditing(null)
              setModalOpen(true)
            }}
          >
            新增账套
          </Button>
          <Text type="secondary" style={{ fontSize: 12 }}>
            切换后财务各页（凭证/科目/应收应付）均按当前账套隔离展示；新账套需先「复制科目」或手工新增科目后才能记账
          </Text>
        </Space>

        <Table<BookVO>
          rowKey="id"
          columns={columns}
          dataSource={books ?? []}
          loading={isLoading || isFetching}
          pagination={false}
        />
      </Card>

      <Modal
        open={modalOpen}
        title={editing ? `修改账套 ${editing.code}` : '新增账套'}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        okText="保存"
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ periodPolicy: 'NATURAL_MONTH', currency: 'CNY', status: 1 }}
          onFinish={(values) => saveMutation.mutate(values)}
        >
          <Form.Item
            name="name"
            label="账套名称"
            rules={[
              { required: true, message: '请输入账套名称' },
              { max: 60, message: '最长 60 字符' },
            ]}
          >
            <Input placeholder="如：华东分公司账套" maxLength={60} />
          </Form.Item>
          <Form.Item name="periodPolicy" label="会计期间政策（当前版本仅支持自然月，预留扩展）" rules={[{ required: true }]}>
            <Select options={[{ value: 'NATURAL_MONTH', label: '自然月' }]} disabled />
          </Form.Item>
          <Form.Item
            name="currency"
            label="本位币（ISO 代码）"
            rules={[
              { required: true, message: '请输入本位币' },
              { pattern: /^[A-Z]{3}$/, message: '3 位大写字母，如 CNY / USD' },
            ]}
          >
            <Input placeholder="CNY" maxLength={3} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true }]}>
            <Select
              style={{ width: 160 }}
              options={[
                { value: 1, label: '启用' },
                { value: 0, label: '停用' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={200} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
