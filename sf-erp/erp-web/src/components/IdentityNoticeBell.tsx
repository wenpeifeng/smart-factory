import { Badge, Button, Divider, Empty, Popover, Space, Tag, Typography } from 'antd'
import { BellOutlined, RightOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import { markRead, pageMessages, unreadCount, type WfMessage } from '@/api/workflow'
import { useAuthStore } from '@/stores/auth'

const { Text } = Typography

export default function IdentityNoticeBell({ dark = false }: { dark?: boolean }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { roles, perms } = useAuthStore()
  const enabled = roles.includes('super_admin') || perms.includes('*:*:*') || perms.includes('workflow:message:list')
  const { data: count = 0 } = useQuery({ queryKey: ['workflow', 'messages', 'unread-count'], queryFn: unreadCount, enabled, refetchInterval: 30_000 })
  const { data } = useQuery({ queryKey: ['workflow', 'messages', 'notice-preview'], queryFn: () => pageMessages({ pageNum: 1, pageSize: 6 }), enabled, refetchInterval: 30_000 })
  const readMutation = useMutation({
    mutationFn: markRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['workflow', 'messages'] }),
  })

  if (!enabled) return null

  const openMessage = (item: WfMessage) => {
    if (item.readFlag === 0) readMutation.mutate(item.id)
    if (item.bizPath) navigate(item.bizPath)
  }
  const items = data?.records ?? []
  const content = (
    <div style={{ width: 390 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Text strong>集团与系统通知</Text>
        <Button type="link" size="small" onClick={() => navigate('/workflow/msgs')}>全部消息 <RightOutlined /></Button>
      </div>
      <Divider style={{ margin: '8px 0' }} />
      {items.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无通知" /> : items.map((item) => {
        const group = item.title.startsWith('[集团通知]')
        return (
          <button
            key={item.id}
            type="button"
            onClick={() => openMessage(item)}
            style={{ width: '100%', border: 0, borderBottom: '1px solid #f0f0f0', background: item.readFlag === 0 ? '#f0f7ff' : '#fff', padding: '10px 8px', textAlign: 'left', cursor: 'pointer' }}
          >
            <Space size={6} style={{ marginBottom: 5 }}>
              <Tag color={group ? 'blue' : 'green'} style={{ marginInlineEnd: 0 }}>{group ? '集团' : '本系统'}</Tag>
              <Text strong={item.readFlag === 0}>{item.title.replace(/^\[[^\]]+\]\s*/, '')}</Text>
            </Space>
            <div style={{ color: '#667085', fontSize: 12, lineHeight: 1.6, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{item.content || '-'}</div>
            <div style={{ color: '#98a2b3', fontSize: 12, marginTop: 4 }}>{dayjs(item.createTime).format('YYYY-MM-DD HH:mm')}</div>
          </button>
        )
      })}
    </div>
  )

  return (
    <Popover placement="bottomRight" trigger="click" content={content}>
      <Badge count={count} size="small" overflowCount={99}>
        <Button type="text" icon={<BellOutlined />} style={{ color: dark ? 'rgba(255,255,255,0.88)' : undefined }}>消息</Button>
      </Badge>
    </Popover>
  )
}
