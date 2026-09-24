import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  App,
  Badge,
  Button,
  Card,
  Select,
  Space,
  Switch,
  Table,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { pageMessages, markRead, markAllRead, unreadCount, type WfMessage } from '@/api/workflow'
import { MSG_TYPE_OPTIONS, MsgTypeTag } from '@/pages/workflow/shared'

const { Title } = Typography

/**
 * 消息中心页：审批通知（新待办/通过/驳回）+ 预警（WARN）。
 * 未读行加粗高亮 + 前置蓝点；点击行即标记已读（带关联路径时跳转）；支持全部已读。
 */
export default function MsgCenter() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const navigate = useNavigate()

  const [msgType, setMsgType] = useState<string | undefined>(undefined)
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['workflow', 'msgs', msgType, unreadOnly, pageNum, pageSize],
    queryFn: () => pageMessages({ pageNum, pageSize, msgType, unreadOnly: unreadOnly || undefined }),
  })
  const { data: unread } = useQuery({
    queryKey: ['workflow', 'msgs', 'unread-count'],
    queryFn: unreadCount,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['workflow', 'msgs'] })
    queryClient.invalidateQueries({ queryKey: ['workflow', 'todo'] })
  }

  const readMutation = useMutation({
    mutationFn: markRead,
    onSuccess: () => invalidate(),
  })

  const readAllMutation = useMutation({
    mutationFn: markAllRead,
    onSuccess: (count) => {
      message.success(`已标记 ${count} 条消息为已读`)
      invalidate()
    },
  })

  /** 点击消息：未读则先标记已读，再按关联路径跳转 */
  const onRowClick = (record: WfMessage) => {
    if (record.readFlag === 0) {
      readMutation.mutate(record.id)
    }
    if (record.bizPath) {
      navigate(record.bizPath)
    }
  }

  const columns: ColumnsType<WfMessage> = [
    {
      title: '',
      dataIndex: 'readFlag',
      width: 40,
      render: (v: number) => (v === 0 ? <Badge status="processing" /> : <Badge status="default" text="" />),
    },
    { title: '类型', dataIndex: 'msgType', width: 90, render: (v) => <MsgTypeTag msgType={v} /> },
    {
      title: '标题',
      dataIndex: 'title',
      ellipsis: true,
      render: (v: string, record) => (
        <span style={{ fontWeight: record.readFlag === 0 ? 600 : 400 }}>{v}</span>
      ),
    },
    {
      title: '内容',
      dataIndex: 'content',
      ellipsis: true,
      render: (v: string | null, record) => (
        <span style={{ color: record.readFlag === 0 ? undefined : '#888' }}>{v ?? '-'}</span>
      ),
    },
    { title: '时间', dataIndex: 'createTime', width: 165 },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, record) =>
        record.readFlag === 0 ? (
          <Button type="link" size="small" onClick={() => readMutation.mutate(record.id)}>
            标记已读
          </Button>
        ) : (
          '-'
        ),
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        消息中心
        {unread != null && unread > 0 && (
          <Badge count={unread} style={{ marginLeft: 12, transform: 'translateY(-3px)' }} />
        )}
      </Title>

      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Select
            placeholder="消息类型"
            style={{ width: 130 }}
            allowClear
            options={MSG_TYPE_OPTIONS}
            value={msgType}
            onChange={(v) => {
              setPageNum(1)
              setMsgType(v)
            }}
          />
          <Space size={6}>
            <Switch checked={unreadOnly} onChange={(v) => { setPageNum(1); setUnreadOnly(v) }} />
            <span style={{ color: '#666' }}>仅未读</span>
          </Space>
          <Button onClick={() => readAllMutation.mutate()} loading={readAllMutation.isPending}>
            全部已读
          </Button>
        </Space>

        <Table<WfMessage>
          rowKey="id"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          onRow={(record) => ({ onClick: () => onRowClick(record), style: { cursor: 'pointer' } })}
          pagination={{
            total: data?.total ?? 0,
            current: data?.pageNum ?? pageNum,
            pageSize: data?.pageSize ?? pageSize,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPageNum(p)
              setPageSize(s)
            },
          }}
        />
      </Card>
    </div>
  )
}
