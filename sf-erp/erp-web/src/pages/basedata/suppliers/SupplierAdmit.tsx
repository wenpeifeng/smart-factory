import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
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
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import {
  approveSupplierAdmit,
  blacklistSupplierAdmit,
  deleteSupplierAdmit,
  getSupplierAdmits,
  rejectSupplierAdmit,
  saveSupplierAdmit,
  submitSupplierAdmit,
  unblacklistSupplierAdmit,
  updateSupplierAdmit,
  type SupplierAdmit,
} from '@/api/basedata'

const { Title } = Typography

/** 准入状态：0草稿 10待审 20通过 30驳回 40黑名单 */
const STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '草稿', color: 'default' },
  10: { label: '待审', color: 'processing' },
  20: { label: '通过', color: 'success' },
  30: { label: '驳回', color: 'error' },
  40: { label: '黑名单', color: 'warning' },
}

/** 准入登记表单值 */
interface AdmitFormValues {
  supplierName: string
  creditCode?: string
  contact?: string
  phone?: string
  address?: string
  licenseFile?: string
  certFiles?: string
  bankInfo?: string
  applyReason?: string
  remark?: string
}

/**
 * 供应商准入列表页（V141）。
 * 登记 → 提交申请（自动挂审批流或管理员简单审批）→ 通过启用 / 驳回 / 黑名单。
 */
export default function SupplierAdmitPage() {
  const queryClient = useQueryClient()
  const [form] = Form.useForm<AdmitFormValues>()

  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<number | undefined>(undefined)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<SupplierAdmit | null>(null)

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['basedata', 'supplier-admit', pageNum, pageSize, keyword, status],
    queryFn: () =>
      getSupplierAdmits({
        pageNum,
        pageSize,
        keyword: keyword || undefined,
        status,
      }),
  })

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['basedata', 'supplier-admit'] })

  const saveMutation = useMutation({
    mutationFn: async (values: AdmitFormValues & { submitNow: boolean }) => {
      const { submitNow, ...payload } = values
      const body = { ...payload, submit: submitNow }
      if (editing) {
        await updateSupplierAdmit(editing.id, body)
      } else {
        await saveSupplierAdmit(body)
      }
    },
    onSuccess: (_d, values) => {
      message.success(values.submitNow ? '已提交申请（待审批）' : '保存成功')
      setOpen(false)
      invalidate()
    },
  })

  const submitMutation = useMutation({
    mutationFn: (id: number) => submitSupplierAdmit(id),
    onSuccess: () => {
      message.success('已提交申请（待审批）')
      invalidate()
    },
  })

  const auditMutation = useMutation({
    mutationFn: async (v: { id: number; pass: boolean; remark?: string }) =>
      v.pass ? approveSupplierAdmit(v.id, v.remark) : rejectSupplierAdmit(v.id, v.remark),
    onSuccess: (_d, v) => {
      message.success(v.pass ? '已通过，供应商已启用' : '已驳回')
      invalidate()
    },
  })

  const blacklistMutation = useMutation({
    mutationFn: (v: { id: number; reason?: string }) => blacklistSupplierAdmit(v.id, v.reason),
    onSuccess: () => {
      message.success('已拉黑，该供应商将被禁止下单')
      invalidate()
    },
  })

  const unblacklistMutation = useMutation({
    mutationFn: (id: number) => unblacklistSupplierAdmit(id),
    onSuccess: () => {
      message.success('已移出黑名单，供应商恢复启用')
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteSupplierAdmit(id),
    onSuccess: () => {
      message.success('删除成功')
      invalidate()
    },
  })

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setOpen(true)
  }

  const openEdit = (record: SupplierAdmit) => {
    setEditing(record)
    form.setFieldsValue({
      supplierName: record.supplierName,
      creditCode: record.creditCode ?? undefined,
      contact: record.contact ?? undefined,
      phone: record.phone ?? undefined,
      address: record.address ?? undefined,
      licenseFile: record.licenseFile ?? undefined,
      certFiles: record.certFiles ?? undefined,
      bankInfo: record.bankInfo ?? undefined,
      applyReason: record.applyReason ?? undefined,
      remark: record.remark ?? undefined,
    })
    setOpen(true)
  }

  const columns: ColumnsType<SupplierAdmit> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '供应商编码', dataIndex: 'supplierCode', width: 130 },
    { title: '供应商名称', dataIndex: 'supplierName', ellipsis: true },
    { title: '信用代码', dataIndex: 'creditCode', width: 170, ellipsis: true },
    { title: '联系人', dataIndex: 'contact', width: 90 },
    { title: '手机号', dataIndex: 'phone', width: 120 },
    {
      title: '准入状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number) => {
        const s = STATUS_MAP[v] ?? { label: String(v), color: 'default' }
        return <Tag color={s.color}>{s.label}</Tag>
      },
    },
    {
      title: '审批意见',
      dataIndex: 'auditRemark',
      width: 140,
      ellipsis: true,
      render: (v: string | null, r) => v ?? r.blacklistReason ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_, record) => {
        const st = record.status
        return (
          <Space size={4} wrap>
            {(st === 0 || st === 30) && (
              <>
                <HasPerm code="basedata:supplierAdmit:edit">
                  <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
                    编辑
                  </Button>
                </HasPerm>
                <HasPerm code="basedata:supplierAdmit:edit">
                  <Popconfirm
                    title="提交后进入审批，确认提交？"
                    onConfirm={() => submitMutation.mutate(record.id)}
                  >
                    <Button type="link" size="small">
                      提交申请
                    </Button>
                  </Popconfirm>
                </HasPerm>
              </>
            )}
            {st === 10 && (
              <HasPerm code="basedata:supplierAdmit:audit">
                <Button
                  type="link"
                  size="small"
                  onClick={() => {
                    Modal.confirm({
                      title: `通过准入申请：${record.supplierName}`,
                      content: (
                        <Input.TextArea
                          id="admit-approve-remark"
                          placeholder="审批意见（可选）"
                          rows={2}
                          maxLength={200}
                        />
                      ),
                      onOk: () => {
                        const el = document.getElementById('admit-approve-remark') as HTMLTextAreaElement | null
                        auditMutation.mutate({ id: record.id, pass: true, remark: el?.value })
                      },
                    })
                  }}
                >
                  通过
                </Button>
                <Button
                  type="link"
                  size="small"
                  danger
                  onClick={() => {
                    Modal.confirm({
                      title: `驳回准入申请：${record.supplierName}`,
                      content: (
                        <Input.TextArea
                          id="admit-reject-remark"
                          placeholder="驳回原因（可选）"
                          rows={2}
                          maxLength={200}
                        />
                      ),
                      onOk: () => {
                        const el = document.getElementById('admit-reject-remark') as HTMLTextAreaElement | null
                        auditMutation.mutate({ id: record.id, pass: false, remark: el?.value })
                      },
                    })
                  }}
                >
                  驳回
                </Button>
              </HasPerm>
            )}
            {st === 20 && (
              <HasPerm code="basedata:supplierAdmit:blacklist">
                <Button
                  type="link"
                  size="small"
                  danger
                  onClick={() => {
                    Modal.confirm({
                      title: `拉黑供应商：${record.supplierName}`,
                      content: (
                        <Input.TextArea
                          id="admit-blacklist-reason"
                          placeholder="拉黑原因"
                          rows={2}
                          maxLength={200}
                        />
                      ),
                      onOk: () => {
                        const el = document.getElementById('admit-blacklist-reason') as HTMLTextAreaElement | null
                        blacklistMutation.mutate({ id: record.id, reason: el?.value })
                      },
                    })
                  }}
                >
                  拉黑
                </Button>
              </HasPerm>
            )}
            {st === 40 && (
              <HasPerm code="basedata:supplierAdmit:blacklist">
                <Popconfirm
                  title="移出黑名单后供应商恢复启用，确认？"
                  onConfirm={() => unblacklistMutation.mutate(record.id)}
                >
                  <Button type="link" size="small">
                    移出黑名单
                  </Button>
                </Popconfirm>
              </HasPerm>
            )}
            {(st === 0 || st === 30) && (
              <HasPerm code="basedata:supplierAdmit:delete">
                <Popconfirm title="确认删除该申请？" onConfirm={() => deleteMutation.mutate(record.id)}>
                  <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                    删除
                  </Button>
                </Popconfirm>
              </HasPerm>
            )}
          </Space>
        )
      },
    },
  ]

  return (
    <Card>
      <Title level={4}>供应商准入</Title>
      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search
          placeholder="供应商名称 / 信用代码"
          allowClear
          style={{ width: 260 }}
          onSearch={(v) => {
            setKeyword(v)
            setPageNum(1)
          }}
        />
        <Select
          placeholder="准入状态"
          allowClear
          style={{ width: 130 }}
          value={status}
          onChange={(v) => {
            setStatus(v)
            setPageNum(1)
          }}
          options={Object.entries(STATUS_MAP).map(([v, s]) => ({ value: Number(v), label: s.label }))}
        />
        <HasPerm code="basedata:supplierAdmit:add">
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            登记新供应商
          </Button>
        </HasPerm>
      </Space>

      <Table<SupplierAdmit>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading || isFetching}
        pagination={{
          current: pageNum,
          pageSize,
          total: data?.total ?? 0,
          showSizeChanger: true,
          onChange: (p, s) => {
            setPageNum(p)
            setPageSize(s)
          },
        }}
      />

      <Modal
        title={editing ? `编辑准入申请（${editing.supplierName}）` : '登记新供应商'}
        open={open}
        onCancel={() => setOpen(false)}
        footer={null}
        width={640}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(v) => saveMutation.mutate({ ...v, submitNow: false })}>
          <Form.Item
            name="supplierName"
            label="供应商名称"
            rules={[{ required: true, message: '请输入供应商名称' }, { max: 100 }]}
          >
            <Input placeholder="公司全称" />
          </Form.Item>
          <Form.Item name="creditCode" label="统一社会信用代码" rules={[{ max: 30 }]}>
            <Input placeholder="18 位信用代码" />
          </Form.Item>
          <Form.Item name="contact" label="联系人" rules={[{ max: 50 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="phone" label="手机号" rules={[{ max: 20 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="address" label="地址" rules={[{ max: 200 }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="licenseFile"
            label="营业执照附件标识"
            rules={[{ max: 500 }]}
            extra="提交申请前必填（当前以文件标识/URL 形式录入，接入文件上传组件后替换）"
          >
            <Input placeholder="如 oss://license/xxx.pdf" />
          </Form.Item>
          <Form.Item name="certFiles" label="其他资质附件（逗号分隔）" rules={[{ max: 1000 }]}>
            <Input placeholder="如 oss://cert/a.pdf,oss://cert/b.pdf" />
          </Form.Item>
          <Form.Item name="bankInfo" label="开户行/账号" rules={[{ max: 200 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="applyReason" label="申请说明" rules={[{ max: 200 }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="remark" label="备注" rules={[{ max: 200 }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Space>
            <Button onClick={() => setOpen(false)}>取消</Button>
            <Button htmlType="submit">存草稿</Button>
            <HasPerm code="basedata:supplierAdmit:edit">
              <Button
                type="primary"
                onClick={() => {
                  form
                    .validateFields()
                    .then((v) => saveMutation.mutate({ ...v, submitNow: true }))
                    .catch(() => undefined)
                }}
              >
                保存并提交申请
              </Button>
            </HasPerm>
          </Space>
        </Form>
      </Modal>
    </Card>
  )
}
