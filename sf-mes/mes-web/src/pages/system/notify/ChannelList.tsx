import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  Checkbox,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Tag,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useTranslation } from 'react-i18next'
import PageHeader from '@/components/PageHeader'
import PageTable from '@/components/PageTable'
import HasPerm from '@/components/HasPerm'
import { useSubmitting } from '@/hooks/useSubmitting'
import { useDebounce } from '@/hooks/useDebounce'
import { pageUsers, type SysUser } from '@/api/system'
import {
  CHANNEL_TYPE_MAP,
  EVENT_BITS,
  EVENT_TYPE_MAP,
  type ChannelFormValues,
  type ChannelType,
  type NotifyBinding,
  type NotifyChannel,
  type NotifySendLog,
  changeChannelStatus,
  createBinding,
  createChannel,
  deleteBinding,
  deleteChannel,
  testChannel,
  updateChannel,
} from '@/api/notify'

/** 通道编辑表单值（eventMask 由勾选位组装；status 用 Switch 承载） */
interface ChannelEditValues extends Omit<ChannelFormValues, 'eventMask' | 'status'> {
  events: { todo: boolean; approval: boolean; warn: boolean }
  status: boolean
}

/** 测试发送弹窗值（钉钉/企微需接收人） */
interface TestValues {
  receiverUserId?: number
}

/** 绑定表单值 */
interface BindingEditValues {
  userId: number
  channel: 'dingtalk' | 'wecom'
  externalId: string
}

/** 组装 event_mask（bit0 待办 / bit1 审批结果 / bit2 预警） */
function maskOf(events: { todo: boolean; approval: boolean; warn: boolean }): number {
  return (events.todo ? 1 : 0) | (events.approval ? 2 : 0) | (events.warn ? 4 : 0)
}

/**
 * 通知通道配置页（管理门户 /admin/system/notify-channels）。
 * <p>
 * 通道三类：webhook 群机器人（钉钉/企微通用，配地址即可用）/ dingtalk 企业内部应用 /
 * wecom 企微自建应用（后两者需 corpId+agentId+secret，并配合"用户绑定"按手机号找人）。
 * 功能：列表 / 新增修改（凭据脱敏回显，空=不改）/ 启停 / 测试发送 / 发送日志 Drawer / 用户绑定 Drawer。
 */
export default function ChannelList() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const debouncedKw = useDebounce(keyword, 300)
  const [channelType, setChannelType] = useState<string | undefined>()
  const [status, setStatus] = useState<number | undefined>()

  const [editing, setEditing] = useState<NotifyChannel | null>(null)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<ChannelEditValues>()
  const watchType = Form.useWatch('channelType', form) as ChannelType | undefined

  // 测试发送
  const [testing, setTesting] = useState<NotifyChannel | null>(null)
  const [testForm] = Form.useForm<TestValues>()

  // 发送日志 Drawer
  const [logChannel, setLogChannel] = useState<NotifyChannel | null>(null)
  const [logEventType, setLogEventType] = useState<string | undefined>()
  const [logSuccess, setLogSuccess] = useState<number | undefined>()

  // 用户绑定 Drawer
  const [bindingOpen, setBindingOpen] = useState(false)
  const [bindingModalOpen, setBindingModalOpen] = useState(false)
  const [bindingForm] = Form.useForm<BindingEditValues>()

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['notify', 'channels'] })
  const refreshLogs = () => queryClient.invalidateQueries({ queryKey: ['notify', 'send-logs'] })
  const refreshBindings = () => queryClient.invalidateQueries({ queryKey: ['notify', 'bindings'] })

  /** 用后台用户搜索（测试发送/绑定选择的接收人） */
  const { data: userOptions } = useQuery({
    queryKey: ['notify', 'users'],
    queryFn: () => pageUsers({ pageNum: 1, pageSize: 100, status: 1 }),
    staleTime: 60_000,
  })
  const users: SysUser[] = userOptions?.records ?? []

  /** 新增/编辑提交 */
  const submit = useSubmitting(
    async (values: ChannelEditValues) => {
      const body: ChannelFormValues = {
        channelType: values.channelType,
        channelName: values.channelName,
        webhookUrl: values.webhookUrl || '',
        corpId: values.corpId || '',
        agentId: values.agentId || '',
        appSecret: values.appSecret || '',
        secret: values.secret || '',
        token: values.token || '',
        aesKey: values.aesKey || '',
        eventMask: maskOf(values.events ?? { todo: false, approval: false, warn: false }),
        mobileBaseUrl: values.mobileBaseUrl || '',
        status: values.status === false ? 0 : 1,
        remark: values.remark || '',
      }
      if (editing) await updateChannel(editing.id, body)
      else await createChannel(body)
    },
    { successText: t('common.success'), onSuccess: () => { setOpen(false); refresh() } },
  )

  /** 启停 */
  const toggleStatus = useSubmitting(
    async (row: NotifyChannel) => {
      await changeChannelStatus(row.id, row.status === 1 ? 0 : 1)
    },
    { successText: t('common.success'), onSuccess: refresh },
  )

  /** 删除 */
  const remove = useSubmitting(
    async (row: NotifyChannel) => {
      await deleteChannel(row.id)
    },
    { successText: t('common.success'), onSuccess: refresh },
  )

  /** 测试发送 */
  const runTest = useSubmitting(
    async () => {
      if (!testing) return
      const values = await testForm.validateFields()
      const log = await testChannel(testing.id, values.receiverUserId)
      refreshLogs()
      Modal.success({
        title: t('notify.testSent'),
        content: `${log.title} · ${log.success === 1 ? t('notify.sendOk') : t('notify.sendFail')}${
          log.costMs != null ? ` · ${log.costMs}ms` : ''
        }`,
      })
    },
    { successText: null, onSuccess: () => setTesting(null) },
  )

  /** 新增绑定 */
  const submitBinding = useSubmitting(
    async (values: BindingEditValues) => {
      await createBinding(values)
    },
    { successText: t('common.success'), onSuccess: () => { setBindingModalOpen(false); refreshBindings() } },
  )

  const openForm = (row?: NotifyChannel) => {
    setEditing(row ?? null)
    setOpen(true)
    const mask = row?.eventMask ?? 7
    form.setFieldsValue(
      row
        ? {
            channelType: row.channelType,
            channelName: row.channelName,
            webhookUrl: row.credentials?.webhookUrl ?? '',
            corpId: row.credentials?.corpId ?? '',
            agentId: row.credentials?.agentId ?? '',
            appSecret: row.credentials?.appSecret ?? '',
            secret: row.credentials?.secret ?? '',
            token: row.credentials?.token ?? '',
            aesKey: row.credentials?.aesKey ?? row.credentials?.encodingAesKey ?? '',
            events: {
              todo: (mask & 1) !== 0,
              approval: (mask & 2) !== 0,
              warn: (mask & 4) !== 0,
            },
            mobileBaseUrl: row.mobileBaseUrl ?? '',
            status: row.status === 1,
            remark: row.remark ?? '',
          }
        : {
            channelType: 'webhook',
            channelName: '',
            webhookUrl: '',
            corpId: '',
            agentId: '',
            appSecret: '',
            secret: '',
            token: '',
            aesKey: '',
            events: { todo: true, approval: true, warn: true },
            mobileBaseUrl: '',
            status: true,
            remark: '',
          },
    )
  }

  const columns: ColumnsType<NotifyChannel> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: t('notify.channelName'), dataIndex: 'channelName', width: 160 },
    {
      title: t('notify.channelType'),
      dataIndex: 'channelType',
      width: 110,
      render: (v: ChannelType) => {
        const color = v === 'webhook' ? 'blue' : v === 'dingtalk' ? 'geekblue' : 'green'
        return <Tag color={color}>{CHANNEL_TYPE_MAP[v]}</Tag>
      },
    },
    {
      title: t('notify.events'),
      dataIndex: 'eventMask',
      width: 200,
      render: (mask: number) => (
        <Space size={4} wrap>
          {EVENT_BITS.filter((e) => (mask & e.bit) !== 0).map((e) => (
            <Tag key={e.bit}>{e.label}</Tag>
          ))}
          {mask === 0 && <Tag color="warning">{t('notify.noEvents')}</Tag>}
        </Space>
      ),
    },
    {
      title: t('notify.mobileBaseUrl'),
      dataIndex: 'mobileBaseUrl',
      width: 200,
      ellipsis: true,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('notify.status'),
      dataIndex: 'status',
      width: 80,
      render: (v: number) =>
        v === 1 ? <Tag color="success">{t('notify.enabled')}</Tag> : <Tag>{t('notify.disabled')}</Tag>,
    },
    { title: t('notify.remark'), dataIndex: 'remark', ellipsis: true, render: (v) => v ?? '-' },
    { title: t('notify.createTime'), dataIndex: 'createTime', width: 165 },
    {
      title: t('notify.action'),
      key: 'action',
      width: 260,
      fixed: 'right',
      render: (_, row) => (
        <Space size={0}>
          <HasPerm code="system:notify:edit">
            <Button type="link" size="small" onClick={() => openForm(row)}>
              {t('common.edit')}
            </Button>
          </HasPerm>
          <HasPerm code="system:notify:test">
            <Button type="link" size="small" onClick={() => { setTesting(row); testForm.resetFields() }}>
              {t('notify.test')}
            </Button>
          </HasPerm>
          <Button type="link" size="small" onClick={() => setLogChannel(row)}>
            {t('notify.sendLog')}
          </Button>
          <HasPerm code="system:notify:edit">
            <Popconfirm
              title={row.status === 1 ? t('notify.disableConfirm') : t('notify.enableConfirm')}
              onConfirm={() => toggleStatus.run(row)}
            >
              <Button type="link" size="small" danger={row.status === 1}>
                {row.status === 1 ? t('notify.disable') : t('notify.enable')}
              </Button>
            </Popconfirm>
          </HasPerm>
          <HasPerm code="system:notify:delete">
            <Popconfirm title={t('notify.deleteConfirm')} onConfirm={() => remove.run(row)}>
              <Button type="link" size="small" danger>
                {t('common.delete')}
              </Button>
            </Popconfirm>
          </HasPerm>
        </Space>
      ),
    },
  ]

  const logColumns: ColumnsType<NotifySendLog> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    {
      title: t('notify.event'),
      dataIndex: 'eventType',
      width: 90,
      render: (v: NotifySendLog['eventType']) => <Tag>{EVENT_TYPE_MAP[v] ?? v}</Tag>,
    },
    {
      title: t('notify.result'),
      dataIndex: 'success',
      width: 80,
      render: (v: number) =>
        v === 1 ? <Tag color="success">{t('notify.sendOk')}</Tag> : <Tag color="error">{t('notify.sendFail')}</Tag>,
    },
    { title: t('notify.receiver'), dataIndex: 'receiver', width: 150, ellipsis: true, render: (v) => v ?? '-' },
    { title: t('notify.title'), dataIndex: 'title', width: 200, ellipsis: true },
    {
      title: t('notify.cost'),
      dataIndex: 'costMs',
      width: 90,
      render: (v: number | null) => (v != null ? `${v}ms` : '-'),
    },
    { title: t('notify.sendTime'), dataIndex: 'createTime', width: 165 },
    {
      title: t('notify.content'),
      dataIndex: 'content',
      ellipsis: true,
      render: (v, row) => (
        <Tooltip title={<div style={{ whiteSpace: 'pre-wrap' }}>{v}{row.errorMsg ? `\n[${row.errorMsg}]` : ''}</div>} placement="topLeft">
          {row.errorMsg ?? v}
        </Tooltip>
      ),
    },
  ]

  const bindingColumns: ColumnsType<NotifyBinding> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: t('notify.user'), dataIndex: 'userName', width: 130, render: (v, row) => v ?? row.userId },
    {
      title: t('notify.channelType'),
      dataIndex: 'channel',
      width: 100,
      render: (v: 'dingtalk' | 'wecom') => <Tag>{CHANNEL_TYPE_MAP[v]}</Tag>,
    },
    { title: t('notify.externalId'), dataIndex: 'externalId', width: 150 },
    { title: t('notify.createTime'), dataIndex: 'createTime', width: 165 },
    {
      title: t('notify.action'),
      key: 'action',
      width: 80,
      render: (_, row) => (
        <HasPerm code="system:notify:edit">
          <Popconfirm title={t('notify.deleteBindingConfirm')} onConfirm={() => deleteBinding(row.id).then(refreshBindings)}>
            <Button type="link" size="small" danger>
              {t('common.delete')}
            </Button>
          </Popconfirm>
        </HasPerm>
      ),
    },
  ]

  const userSelectOptions = users.map((u) => ({
    value: u.id,
    label: `${u.nickname}(${u.username})`,
  }))

  return (
    <div className="erp-page">
      <PageHeader
        title={t('admin.notifyChannels')}
        subtitle={t('notify.subtitle')}
        extra={
          <Space>
            <Button onClick={() => setBindingOpen(true)}>{t('notify.binding')}</Button>
            <HasPerm code="system:notify:edit">
              <Button type="primary" onClick={() => openForm()}>
                {t('common.add')}
              </Button>
            </HasPerm>
          </Space>
        }
      />
      <Card>
        <PageTable<NotifyChannel>
          url="/notify/channels"
          queryKey={['notify', 'channels', debouncedKw, channelType, status]}
          params={{ keyword: debouncedKw || undefined, channelType, status }}
          columns={columns}
          toolbar={
            <Space wrap>
              <Input.Search
                placeholder={t('common.keywordPlaceholder')}
                style={{ width: 200 }}
                allowClear
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
              />
              <Select
                style={{ width: 120 }}
                allowClear
                placeholder={t('notify.channelType')}
                value={channelType}
                onChange={setChannelType}
                options={Object.entries(CHANNEL_TYPE_MAP).map(([value, label]) => ({ value, label }))}
              />
              <Select
                style={{ width: 110 }}
                allowClear
                placeholder={t('notify.status')}
                value={status}
                onChange={setStatus}
                options={[
                  { value: 1, label: t('notify.enabled') },
                  { value: 0, label: t('notify.disabled') },
                ]}
              />
            </Space>
          }
        />
        <div style={{ color: '#8c939c', fontSize: 12, marginTop: 8 }}>{t('notify.hint')}</div>
      </Card>

      {/* 新增/编辑通道 */}
      <Modal
        title={`${editing ? t('common.edit') : t('common.add')} · ${t('admin.notifyChannels')}`}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.validateFields().then((v) => submit.run(v))}
        confirmLoading={submit.loading}
        destroyOnClose
        width={560}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 12 }}>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="channelType" label={t('notify.channelType')} style={{ width: 160 }}
              rules={[{ required: true, message: t('notify.channelTypeRequired') }]}>
              <Select
                disabled={!!editing}
                options={Object.entries(CHANNEL_TYPE_MAP).map(([value, label]) => ({ value, label }))}
              />
            </Form.Item>
            <Form.Item name="channelName" label={t('notify.channelName')} style={{ flex: 1, minWidth: 220 }}
              rules={[{ required: true, message: t('notify.channelNameRequired') }]}>
              <Input maxLength={60} placeholder={t('notify.channelNamePlaceholder')} />
            </Form.Item>
          </Space>

          {watchType === 'webhook' && (
            <Form.Item
              name="webhookUrl"
              label={t('notify.webhookUrl')}
              extra={editing ? t('notify.maskedKeepHint') : t('notify.webhookUrlHint')}
              rules={[{ required: true, message: t('notify.webhookUrlRequired') }]}
            >
              <Input placeholder="https://oapi.dingtalk.com/robot/send?access_token=..." />
            </Form.Item>
          )}
          {watchType === 'dingtalk' && (
            <>
              <Space size={16} style={{ display: 'flex' }}>
                <Form.Item name="corpId" label="CorpId" style={{ flex: 1, minWidth: 200 }}
                  rules={[{ required: true, message: t('notify.corpIdRequired') }]}>
                  <Input placeholder="dingxxxxxxxx" />
                </Form.Item>
                <Form.Item name="agentId" label="AgentId" style={{ flex: 1, minWidth: 160 }}
                  rules={[{ required: true, message: t('notify.agentIdRequired') }]}>
                  <Input placeholder="123456789" />
                </Form.Item>
              </Space>
              <Form.Item name="appSecret" label="AppSecret"
                extra={editing ? t('notify.maskedKeepHint') : undefined}
                rules={[{ required: true, message: t('notify.secretRequired') }]}>
                <Input.Password placeholder="xxxxxxxx" />
              </Form.Item>
            </>
          )}
          {watchType === 'wecom' && (
            <>
              <Space size={16} style={{ display: 'flex' }}>
                <Form.Item name="corpId" label="CorpId" style={{ flex: 1, minWidth: 200 }}
                  rules={[{ required: true, message: t('notify.corpIdRequired') }]}>
                  <Input placeholder="wwxxxxxxxx" />
                </Form.Item>
                <Form.Item name="agentId" label="AgentId" style={{ flex: 1, minWidth: 160 }}
                  rules={[{ required: true, message: t('notify.agentIdRequired') }]}>
                  <Input placeholder="1000002" />
                </Form.Item>
              </Space>
              <Form.Item name="secret" label="Secret"
                extra={editing ? t('notify.maskedKeepHint') : undefined}
                rules={[{ required: true, message: t('notify.secretRequired') }]}>
                <Input.Password placeholder="xxxxxxxx" />
              </Form.Item>
            </>
          )}
          {watchType !== 'webhook' && (
            <Space size={16} style={{ display: 'flex' }}>
              <Form.Item name="token" label={`Token(${t('notify.optional')})`} style={{ flex: 1, minWidth: 200 }}>
                <Input placeholder={t('notify.tokenHint')} />
              </Form.Item>
              <Form.Item name="aesKey" label={`AESKey(${t('notify.optional')})`} style={{ flex: 1, minWidth: 220 }}>
                <Input placeholder={t('notify.aesKeyHint')} />
              </Form.Item>
            </Space>
          )}

          <Form.Item label={t('notify.events')} required>
            <Space size={24}>
              <Form.Item name={['events', 'todo']} valuePropName="checked" noStyle>
                <Checkbox>{t('notify.todoPush')}</Checkbox>
              </Form.Item>
              <Form.Item name={['events', 'approval']} valuePropName="checked" noStyle>
                <Checkbox>{t('notify.approvalResult')}</Checkbox>
              </Form.Item>
              <Form.Item name={['events', 'warn']} valuePropName="checked" noStyle>
                <Checkbox>{t('notify.warn')}</Checkbox>
              </Form.Item>
            </Space>
          </Form.Item>
          <Form.Item name="mobileBaseUrl" label={t('notify.mobileBaseUrl')} extra={t('notify.mobileBaseUrlHint')}>
            <Input placeholder="http://erp.example.com:5173" />
          </Form.Item>
          <Form.Item name="status" label={t('notify.status')} valuePropName="checked">
            <Switch checkedChildren={t('notify.enabled')} unCheckedChildren={t('notify.disabled')} />
          </Form.Item>
          <Form.Item name="remark" label={t('notify.remark')}>
            <Input.TextArea rows={2} maxLength={200} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 测试发送 */}
      <Modal
        title={`${t('notify.test')} · ${testing?.channelName ?? ''}`}
        open={!!testing}
        onCancel={() => setTesting(null)}
        onOk={() => runTest.run()}
        confirmLoading={runTest.loading}
        destroyOnClose
      >
        <Form form={testForm} layout="vertical" style={{ marginTop: 12 }}>
          {testing && testing.channelType !== 'webhook' && (
            <Form.Item
              name="receiverUserId"
              label={t('notify.receiverUser')}
              rules={[{ required: true, message: t('notify.receiverUserRequired') }]}
              extra={t('notify.receiverUserHint')}
            >
              <Select
                showSearch
                optionFilterProp="label"
                placeholder={t('notify.receiverUserPlaceholder')}
                options={userSelectOptions}
              />
            </Form.Item>
          )}
          <div style={{ color: '#8c939c', fontSize: 12 }}>{t('notify.testHint')}</div>
        </Form>
      </Modal>

      {/* 发送日志 Drawer */}
      <Drawer
        title={`${t('notify.sendLog')} · ${logChannel?.channelName ?? ''}`}
        open={!!logChannel}
        onClose={() => setLogChannel(null)}
        width={960}
        destroyOnClose
      >
        <Space wrap style={{ marginBottom: 12 }}>
          <Select
            style={{ width: 130 }}
            allowClear
            placeholder={t('notify.event')}
            value={logEventType}
            onChange={setLogEventType}
            options={Object.entries(EVENT_TYPE_MAP).map(([value, label]) => ({ value, label }))}
          />
          <Select
            style={{ width: 110 }}
            allowClear
            placeholder={t('notify.result')}
            value={logSuccess}
            onChange={setLogSuccess}
            options={[
              { value: 1, label: t('notify.sendOk') },
              { value: 0, label: t('notify.sendFail') },
            ]}
          />
        </Space>
        <PageTable<NotifySendLog>
          url="/notify/send-logs"
          queryKey={['notify', 'send-logs', logChannel?.id, logEventType, logSuccess]}
          params={{ channelId: logChannel?.id, eventType: logEventType, success: logSuccess }}
          columns={logColumns}
        />
      </Drawer>

      {/* 用户绑定 Drawer */}
      <Drawer
        title={t('notify.binding')}
        open={bindingOpen}
        onClose={() => setBindingOpen(false)}
        width={720}
        destroyOnClose
        extra={
          <HasPerm code="system:notify:edit">
            <Button
              type="primary"
              size="small"
              onClick={() => {
                bindingForm.resetFields()
                setBindingModalOpen(true)
              }}
            >
              {t('notify.addBinding')}
            </Button>
          </HasPerm>
        }
      >
        <div style={{ color: '#8c939c', fontSize: 12, marginBottom: 12 }}>{t('notify.bindingHint')}</div>
        <PageTable<NotifyBinding>
          url="/notify/bindings"
          queryKey={['notify', 'bindings']}
          columns={bindingColumns}
        />
      </Drawer>

      {/* 新增绑定 */}
      <Modal
        title={t('notify.addBinding')}
        open={bindingModalOpen}
        onCancel={() => setBindingModalOpen(false)}
        onOk={() => bindingForm.validateFields().then((v) => submitBinding.run(v))}
        confirmLoading={submitBinding.loading}
        destroyOnClose
      >
        <Form form={bindingForm} layout="vertical" style={{ marginTop: 12 }}>
          <Form.Item name="userId" label={t('notify.user')}
            rules={[{ required: true, message: t('notify.receiverUserRequired') }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder={t('notify.receiverUserPlaceholder')}
              options={userSelectOptions}
            />
          </Form.Item>
          <Form.Item name="channel" label={t('notify.channelType')}
            rules={[{ required: true, message: t('notify.channelTypeRequired') }]}>
            <Select
              options={[
                { value: 'dingtalk', label: CHANNEL_TYPE_MAP.dingtalk },
                { value: 'wecom', label: CHANNEL_TYPE_MAP.wecom },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="externalId"
            label={t('notify.externalId')}
            extra={t('notify.externalIdHint')}
            rules={[{ required: true, message: t('notify.externalIdRequired') }]}
          >
            <Input placeholder="13800000000" maxLength={64} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
