import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  Form,
  Modal,
  Pagination,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  INBOUND_BIZ_TYPE_OPTIONS,
  OUTBOX_BIZ_TYPE_OPTIONS,
  OUTBOX_STATUS_OPTIONS,
  integrationApi,
  type InboundLogRow,
  type OutboxRow,
} from '@/api/integration'
import HasPerm from '@/components/HasPerm'
import { formatDateTime } from '@/utils/date'

const { Title } = Typography

function formatPayload(payload?: string): string {
  if (!payload) return '-'

  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload
  }
}

/**
 * 集成监控页（ERP-MES）：outbox 出站监控 + 入站消息日志两 Tab。
 * - outbox：状态筛选、死信/待发手动重试、报文查看；
 * - 入站日志：业务类型/状态筛选、DUPLICATE 幂等跳过可视。
 * 对账补偿入口：主数据手动全量推送（物料/BOM/工艺路线入 outbox）。
 */
export default function IntegrationMonitor() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [outboxParams, setOutboxParams] = useState({ pageNum: 1, pageSize: 10 })
  const [logParams, setLogParams] = useState({ pageNum: 1, pageSize: 10 })
  const [viewing, setViewing] = useState<{ title: string; payload: string } | null>(null)

  const { data: outbox, isLoading: outboxLoading } = useQuery({
    queryKey: ['integration', 'outbox', outboxParams],
    queryFn: () => integrationApi.outboxPage(outboxParams),
  })

  const { data: logs, isLoading: logLoading } = useQuery({
    queryKey: ['integration', 'inbound-logs', logParams],
    queryFn: () => integrationApi.inboundLogPage(logParams),
  })

  const invalidateOutbox = () => queryClient.invalidateQueries({ queryKey: ['integration', 'outbox'] })

  const retryMutation = useMutation({
    mutationFn: integrationApi.outboxRetry,
    onSuccess: () => {
      message.success('重试已执行')
      invalidateOutbox()
    },
  })

  const pushMutation = useMutation({
    mutationFn: integrationApi.pushMasterdata,
    onSuccess: (summary) => {
      message.success(`对账推送完成：${summary}`)
      invalidateOutbox()
    },
  })

  const statusTag = (status: string) => {
    if (status === 'SUCCESS') return <Tag color="green">成功</Tag>
    if (status === 'DEAD') return <Tag color="red">死信</Tag>
    if (status === 'DUPLICATE') return <Tag color="orange">重复跳过</Tag>
    return <Tag color="blue">待发/重试中</Tag>
  }

  const outboxColumns: ColumnsType<OutboxRow> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: '业务类型',
      dataIndex: 'bizType',
      width: 150,
      render: (v: string) => OUTBOX_BIZ_TYPE_OPTIONS.find((o) => o.value === v)?.label ?? v,
    },
    { title: '业务ID', dataIndex: 'bizId', width: 90 },
    { title: '状态', dataIndex: 'status', width: 110, render: statusTag },
    { title: '重试次数', dataIndex: 'retryCount', width: 90 },
    { title: '事件时间', dataIndex: 'eventTime', width: 180, render: formatDateTime },
    { title: '下次重试', dataIndex: 'nextRetryTime', width: 180, render: formatDateTime },
    { title: '成功时间', dataIndex: 'successTime', width: 180, render: formatDateTime },
    {
      title: '操作',
      width: 160,
      fixed: 'right',
      render: (_, row) => (
        <Space>
          <Button size="small" onClick={() => setViewing({ title: `outbox #${row.id} 报文`, payload: row.payload })}>
            报文
          </Button>
          <HasPerm code={['integration:outbox:retry']}>
            {row.status !== 'SUCCESS' && (
              <Popconfirm title="确认手动重试该记录？" onConfirm={() => retryMutation.mutate(row.id)}>
                <Button size="small" type="primary" danger={row.status === 'DEAD'}>
                  重试
                </Button>
              </Popconfirm>
            )}
          </HasPerm>
        </Space>
      ),
    },
  ]

  const logColumns: ColumnsType<InboundLogRow> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: '业务类型',
      dataIndex: 'bizType',
      width: 130,
      render: (v: string) => INBOUND_BIZ_TYPE_OPTIONS.find((o) => o.value === v)?.label ?? v,
    },
    { title: '幂等键', dataIndex: 'bizKey', width: 200 },
    { title: '状态', dataIndex: 'status', width: 110, render: statusTag },
    { title: '事件时间', dataIndex: 'eventTime', width: 180, render: formatDateTime },
    { title: '结果摘要', dataIndex: 'error', ellipsis: true },
    {
      title: '操作',
      width: 90,
      fixed: 'right',
      render: (_, row) => (
        <Button size="small" onClick={() => setViewing({ title: `入站日志 #${row.id} 报文`, payload: row.payload })}>
          报文
        </Button>
      ),
    },
  ]

  return (
    <Card>
      <Title level={4}>集成监控（ERP-MES）</Title>
      <Tabs
        items={[
          {
            key: 'outbox',
            label: '出站 Outbox',
            children: (
              <>
                <Space style={{ marginBottom: 16 }} wrap>
                  <Form layout="inline">
                    <Form.Item label="状态" style={{ marginRight: 8 }}>
                      <Select
                        allowClear
                        placeholder="全部状态"
                        style={{ width: 140 }}
                        options={OUTBOX_STATUS_OPTIONS}
                        onChange={(v) => setOutboxParams((p) => ({ ...p, status: v, pageNum: 1 }))}
                      />
                    </Form.Item>
                    <Form.Item label="业务类型">
                      <Select
                        allowClear
                        placeholder="全部类型"
                        style={{ width: 150 }}
                        options={OUTBOX_BIZ_TYPE_OPTIONS}
                        onChange={(v) => setOutboxParams((p) => ({ ...p, bizType: v, pageNum: 1 }))}
                      />
                    </Form.Item>
                  </Form>
                  <HasPerm code={['integration:masterdata:push']}>
                    <Popconfirm
                      title="全量推送物料/BOM/工艺路线到 MES（幂等对账）？"
                      onConfirm={() => pushMutation.mutate()}
                    >
                      <Button>主数据对账推送</Button>
                    </Popconfirm>
                  </HasPerm>
                </Space>
                <Table
                  className="erp-nowrap-table"
                  rowKey="id"
                  size="small"
                  loading={outboxLoading}
                  columns={outboxColumns}
                  dataSource={outbox?.records ?? []}
                  pagination={false}
                  tableLayout="fixed"
                  scroll={{ x: 1290 }}
                />
                <Pagination
                  style={{ marginTop: 12, textAlign: 'right' }}
                  current={outbox?.pageNum}
                  pageSize={outbox?.pageSize}
                  total={outbox?.total}
                  showSizeChanger
                  showTotal={(t) => `共 ${t} 条`}
                  onChange={(pageNum, pageSize) => setOutboxParams({ pageNum, pageSize })}
                />
              </>
            ),
          },
          {
            key: 'inbound-logs',
            label: '入站日志',
            children: (
              <>
                <Space style={{ marginBottom: 16 }} wrap>
                  <Form layout="inline">
                    <Form.Item label="业务类型" style={{ marginRight: 8 }}>
                      <Select
                        allowClear
                        placeholder="全部类型"
                        style={{ width: 140 }}
                        options={INBOUND_BIZ_TYPE_OPTIONS}
                        onChange={(v) => setLogParams((p) => ({ ...p, bizType: v, pageNum: 1 }))}
                      />
                    </Form.Item>
                    <Form.Item label="状态">
                      <Select
                        allowClear
                        placeholder="全部状态"
                        style={{ width: 140 }}
                        options={[
                          { value: 'SUCCESS', label: '已处理' },
                          { value: 'DUPLICATE', label: '重复跳过' },
                        ]}
                        onChange={(v) => setLogParams((p) => ({ ...p, status: v, pageNum: 1 }))}
                      />
                    </Form.Item>
                  </Form>
                </Space>
                <Table
                  className="erp-nowrap-table"
                  rowKey="id"
                  size="small"
                  loading={logLoading}
                  columns={logColumns}
                  dataSource={logs?.records ?? []}
                  pagination={false}
                  tableLayout="fixed"
                  scroll={{ x: 980 }}
                />
                <Pagination
                  style={{ marginTop: 12, textAlign: 'right' }}
                  current={logs?.pageNum}
                  pageSize={logs?.pageSize}
                  total={logs?.total}
                  showSizeChanger
                  showTotal={(t) => `共 ${t} 条`}
                  onChange={(pageNum, pageSize) => setLogParams({ pageNum, pageSize })}
                />
              </>
            ),
          },
        ]}
      />
      <Modal
        open={viewing !== null}
        title={viewing?.title}
        footer={null}
        width={1080}
        onCancel={() => setViewing(null)}
        centered
        styles={{ body: { maxHeight: '70vh', overflow: 'hidden' } }}
      >
        <div style={{ color: '#64748b', marginBottom: 8 }}>报文内容</div>
        <pre
          style={{
            minHeight: 360,
            maxHeight: '62vh',
            overflow: 'auto',
            margin: 0,
            padding: 16,
            border: '1px solid #dbe4ef',
            borderRadius: 8,
            background: '#f7f9fc',
            color: '#17324d',
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
            fontSize: 13,
            lineHeight: 1.65,
            whiteSpace: 'pre',
          }}
        >
          {formatPayload(viewing?.payload)}
        </pre>
      </Modal>
    </Card>
  )
}
