import { useMemo, useState } from 'react'
import { DatePicker, Input, Select, Space, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { Dayjs } from 'dayjs'
import { SearchOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import PageTable from '@/components/PageTable'
import StatusTag from '@/components/StatusTag'
import { useDebounce } from '@/hooks/useDebounce'

interface LoginLogRow {
  id: number
  username: string
  userId: number | null
  event: 'LOGIN' | 'LOGOUT'
  status: number
  errorMsg: string | null
  ip: string | null
  userAgent: string | null
  costMs: number | null
  createTime: string
}

/** 登录、登出审计日志（只读）。 */
export default function LoginLogList() {
  const [username, setUsername] = useState('')
  const [event, setEvent] = useState<string>()
  const [status, setStatus] = useState<number>()
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)
  const debouncedUsername = useDebounce(username, 300)

  const beginTime = range?.[0]?.startOf('day').format('YYYY-MM-DDTHH:mm:ss')
  const endTime = range?.[1]?.endOf('day').format('YYYY-MM-DDTHH:mm:ss')

  const columns = useMemo<ColumnsType<LoginLogRow>>(
    () => [
      { title: '账号', dataIndex: 'username', width: 140 },
      {
        title: '事件',
        dataIndex: 'event',
        width: 90,
        render: (value: LoginLogRow['event']) => (
          <Tag color={value === 'LOGIN' ? 'blue' : 'default'}>{value === 'LOGIN' ? '登录' : '登出'}</Tag>
        ),
      },
      {
        title: '结果',
        dataIndex: 'status',
        width: 90,
        render: (value: number) => (
          <StatusTag value={value === 1 ? '成功' : '失败'} semantic={value === 1 ? 'success' : 'error'} />
        ),
      },
      { title: '失败原因', dataIndex: 'errorMsg', width: 180, ellipsis: true, render: (value) => value ?? '-' },
      { title: 'IP 地址', dataIndex: 'ip', width: 140, render: (value) => value ?? '-' },
      { title: '客户端', dataIndex: 'userAgent', ellipsis: true, render: (value) => value ?? '-' },
      { title: '耗时', dataIndex: 'costMs', width: 90, render: (value) => value == null ? '-' : `${value} ms` },
      { title: '时间', dataIndex: 'createTime', width: 180 },
    ],
    [],
  )

  return (
    <div className="erp-page">
      <PageHeader title="登录日志" subtitle="查看本系统的登录、登出及失败尝试" />
      <PageTable<LoginLogRow>
        title="日志列表"
        url="/system/login-logs"
        queryKey={['system', 'login-logs', debouncedUsername, event, status, beginTime, endTime]}
        params={{ username: debouncedUsername || undefined, event, status, beginTime, endTime }}
        columns={columns}
        toolbar={
          <Space wrap>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="搜索账号"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              style={{ width: 220 }}
            />
            <Select
              allowClear
              placeholder="事件"
              value={event}
              onChange={setEvent}
              style={{ width: 120 }}
              options={[{ value: 'LOGIN', label: '登录' }, { value: 'LOGOUT', label: '登出' }]}
            />
            <Select
              allowClear
              placeholder="结果"
              value={status}
              onChange={setStatus}
              style={{ width: 120 }}
              options={[{ value: 1, label: '成功' }, { value: 0, label: '失败' }]}
            />
            <DatePicker.RangePicker value={range} onChange={(value) => setRange(value)} />
          </Space>
        }
      />
    </div>
  )
}
