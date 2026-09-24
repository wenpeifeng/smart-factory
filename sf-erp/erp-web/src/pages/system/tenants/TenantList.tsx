import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Switch, Tag, Tooltip } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { LockOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '@/components/PageHeader'
import PageTable from '@/components/PageTable'
import { useSubmitting } from '@/hooks/useSubmitting'
import { useDebounce } from '@/hooks/useDebounce'
import { get, post, put } from '@/api/request'

/** 租户行（后端 TenantVO） */
interface TenantRow {
  id: number
  code: string
  name: string
  contact: string | null
  phone: string | null
  status: number
  expireTime: string | null
  remark: string | null
  createTime: string | null
}

/** 租户表单值（status 用 Switch 承载，提交时转 1/0） */
interface TenantFormValues {
  code: string
  name: string
  contact?: string
  phone?: string
  status?: boolean
  remark?: string
}

/** 默认租户 id（服务层保护：不可停用/改码） */
const DEFAULT_TENANT_ID = 1000

/**
 * 租户管理页（管理门户 /admin/system/tenants）。
 * sys_tenant 在租户插件白名单内，平台管理员在此跨租户维护；
 * 后端接口：GET/POST/PUT /system/tenants、PUT /system/tenants/{id}/status。
 */
export default function TenantList() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const debouncedKw = useDebounce(keyword, 300)
  const [status, setStatus] = useState<number | undefined>()
  const [editing, setEditing] = useState<TenantRow | null>(null)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<TenantFormValues>()

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin', 'tenants'] })

  const { data: enabledTenants } = useQuery({
    queryKey: ['admin', 'tenants', 'enabled'],
    queryFn: () => get<TenantRow[]>('/system/tenants/enabled'),
  })

  /** 新增/编辑提交（Switch 布尔 → 后端 1/0） */
  const submit = useSubmitting(
    async (values: TenantFormValues) => {
      const body = { ...values, status: values.status === false ? 0 : 1 }
      if (editing) await put(`/system/tenants/${editing.id}`, body)
      else await post('/system/tenants', body)
    },
    { successText: t('common.success'), onSuccess: () => { setOpen(false); refresh() } },
  )

  /** 启停（默认租户后端保护，前端同时禁用入口） */
  const toggleStatus = useSubmitting(
    async (row: TenantRow) => {
      await put(`/system/tenants/${row.id}/status`, { status: row.status === 1 ? 0 : 1 })
    },
    { successText: t('common.success'), onSuccess: refresh },
  )

  const openForm = (row?: TenantRow) => {
    setEditing(row ?? null)
    setOpen(true)
    form.setFieldsValue(
      row
        ? {
            code: row.code,
            name: row.name,
            contact: row.contact ?? '',
            phone: row.phone ?? '',
            status: row.status === 1,
            remark: row.remark ?? '',
          }
        : { code: '', name: '', contact: '', phone: '', status: true, remark: '' },
    )
  }

  const columns: ColumnsType<TenantRow> = [
    { title: t('tenant.id'), dataIndex: 'id', width: 70 },
    {
      title: t('tenant.code'),
      dataIndex: 'code',
      width: 130,
      render: (v: string, row) =>
        row.id === DEFAULT_TENANT_ID ? (
          <Space size={4}>
            {v}
            <Tooltip title={t('tenant.defaultProtected')}>
              <LockOutlined style={{ color: '#8c939c' }} />
            </Tooltip>
          </Space>
        ) : (
          v
        ),
    },
    { title: t('tenant.name'), dataIndex: 'name', width: 170 },
    { title: t('tenant.contact'), dataIndex: 'contact', width: 100, render: (v) => v ?? '-' },
    { title: t('tenant.phone'), dataIndex: 'phone', width: 120, render: (v) => v ?? '-' },
    {
      title: t('tenant.status'),
      dataIndex: 'status',
      width: 90,
      render: (v: number) =>
        v === 1 ? <Tag color="success">{t('tenant.enabled')}</Tag> : <Tag>{t('tenant.disabled')}</Tag>,
    },
    { title: t('tenant.expire'), dataIndex: 'expireTime', width: 110, render: (v) => v ?? '-' },
    { title: t('tenant.remark'), dataIndex: 'remark', ellipsis: true },
    { title: t('tenant.createTime'), dataIndex: 'createTime', width: 165 },
    {
      title: t('tenant.action'),
      key: 'action',
      width: 150,
      fixed: 'right',
      render: (_, row) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => openForm(row)}>
            {t('common.edit')}
          </Button>
          {row.id !== DEFAULT_TENANT_ID && (
            <Popconfirm
              title={row.status === 1 ? t('tenant.disableConfirm') : t('tenant.enableConfirm')}
              onConfirm={() => toggleStatus.run(row)}
            >
              <Button type="link" size="small" danger={row.status === 1}>
                {row.status === 1 ? t('tenant.disable') : t('tenant.enable')}
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
        title={t('admin.tenants')}
        subtitle={t('tenant.defaultProtected')}
        extra={
          <Button type="primary" onClick={() => openForm()}>
            {t('common.add')}
          </Button>
        }
      />
      <Card>
        <PageTable<TenantRow>
          url="/system/tenants"
          queryKey={['admin', 'tenants', debouncedKw, status]}
          params={{ keyword: debouncedKw || undefined, status }}
          columns={columns}
          toolbar={
            <Space wrap>
              <Input.Search
                placeholder={t('common.keywordPlaceholder')}
                style={{ width: 220 }}
                allowClear
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
              />
              <Select
                style={{ width: 130 }}
                allowClear
                placeholder={t('tenant.status')}
                value={status}
                onChange={setStatus}
                options={[
                  { value: 1, label: t('tenant.enabled') },
                  { value: 0, label: t('tenant.disabled') },
                ]}
              />
            </Space>
          }
        />
        <div style={{ color: '#8c939c', fontSize: 12, marginTop: 8 }}>
          {t('tenant.enabledCount', { count: enabledTenants?.length ?? '-' })}
        </div>
      </Card>

      <Modal
        title={`${editing ? t('common.edit') : t('common.add')} · ${t('admin.tenants')}`}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.validateFields().then((v) => submit.run(v))}
        confirmLoading={submit.loading}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 12 }}>
          <Form.Item
            name="code"
            label={t('tenant.code')}
            rules={[
              { required: true, message: t('tenant.codeRequired') },
              { pattern: /^[A-Za-z0-9_-]{2,40}$/, message: t('tenant.codeRule') },
            ]}
          >
            <Input disabled={editing?.id === DEFAULT_TENANT_ID} placeholder="t2001" />
          </Form.Item>
          <Form.Item
            name="name"
            label={t('tenant.name')}
            rules={[{ required: true, message: t('tenant.nameRequired') }]}
          >
            <Input maxLength={100} />
          </Form.Item>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="contact" label={t('tenant.contact')} style={{ flex: 1, minWidth: 160 }}>
              <Input maxLength={50} />
            </Form.Item>
            <Form.Item name="phone" label={t('tenant.phone')} style={{ flex: 1, minWidth: 160 }}>
              <Input maxLength={20} />
            </Form.Item>
          </Space>
          <Form.Item name="status" label={t('tenant.status')} valuePropName="checked">
            <Switch checkedChildren={t('tenant.enabled')} unCheckedChildren={t('tenant.disabled')} />
          </Form.Item>
          <Form.Item name="remark" label={t('tenant.remark')}>
            <Input.TextArea rows={2} maxLength={200} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
