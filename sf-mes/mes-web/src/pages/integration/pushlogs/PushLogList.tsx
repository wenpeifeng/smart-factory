import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Drawer, Input, Select, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import PageHeader from '@/components/PageHeader'
import { PUSH_BIZ_TYPE, getPushLogs, type PushLog } from '@/api/integration'

const STATUS_COLOR: Record<string, string> = { SUCCESS: 'green', FAILED: 'red', PENDING: 'orange' }

/** ERP 回传推送日志（对账页）：按状态筛 FAILED 即待补偿清单 */
export default function PushLogList() {
  const [keyword, setKeyword] = useState('')
  const [bizType, setBizType] = useState<string | undefined>()
  const [status, setStatus] = useState<string | undefined>()
  const [detail, setDetail] = useState<PushLog | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['integration', 'pushlogs', keyword, bizType, status],
    queryFn: () => getPushLogs({ pageNum: 1, pageSize: 20, keyword: keyword || undefined, bizType, status }),
  })

  const columns: ColumnsType<PushLog> = [
    { title: '业务类型', dataIndex: 'bizType', width: 100, render: (v: string) => PUSH_BIZ_TYPE[v] ?? v },
    { title: '单据号', dataIndex: 'bizNo', width: 160 },
    { title: '状态', dataIndex: 'status', width: 90, render: (v: string) => <Tag color={STATUS_COLOR[v]}>{v}</Tag> },
    { title: '重试', width: 80, render: (_, r) => `${r.retryCount}/${r.maxRetry}` },
    { title: '推送时间', dataIndex: 'pushTime', width: 170, render: (v) => v ?? '-' },
    { title: '失败原因', dataIndex: 'lastError', ellipsis: true, render: (v) => v ?? '-' },
    {
      title: '操作',
      render: (_, r) => (
        <a onClick={() => setDetail(r)}>报文</a>
      ),
    },
  ]

  return (
    <>
      <PageHeader title="回传推送日志" subtitle="MES → ERP 回传的对账与补偿：FAILED 且未超重试上限的记录由定时任务每 5 分钟自动重推" />
      <Table<PushLog>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        title={() => (
          <Space>
            <Input.Search placeholder="单据号" allowClear style={{ width: 200 }} onSearch={setKeyword} />
            <Select allowClear placeholder="业务类型" style={{ width: 120 }} onChange={setBizType} options={Object.entries(PUSH_BIZ_TYPE).map(([value, label]) => ({ value, label }))} />
            <Select allowClear placeholder="状态" style={{ width: 110 }} onChange={setStatus} options={[{ value: 'PENDING', label: '待推送' }, { value: 'SUCCESS', label: '成功' }, { value: 'FAILED', label: '失败' }]} />
          </Space>
        )}
      />
      <Drawer title={`推送报文：${detail?.bizNo ?? ''}`} width={560} open={detail != null} onClose={() => setDetail(null)}>
        <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all', fontSize: 12 }}>{detail?.payload}</pre>
      </Drawer>
    </>
  )
}
