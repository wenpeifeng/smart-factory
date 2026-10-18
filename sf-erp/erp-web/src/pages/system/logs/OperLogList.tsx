import { useMemo, useState } from 'react'
import { DatePicker, Input, Select, Space, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { Dayjs } from 'dayjs'
import { SearchOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import PageTable from '@/components/PageTable'
import StatusTag from '@/components/StatusTag'
import { useDebounce } from '@/hooks/useDebounce'
import type { SysOperLog } from '@/api/system'

/** 管理操作审计日志（只读）。 */
export default function OperLogList() {
  const [module, setModule] = useState('')
  const [operatorName, setOperatorName] = useState('')
  const [status, setStatus] = useState<number>()
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)
  const debouncedModule = useDebounce(module, 300)
  const debouncedOperator = useDebounce(operatorName, 300)

  const beginTime = range?.[0]?.startOf('day').format('YYYY-MM-DDTHH:mm:ss')
  const endTime = range?.[1]?.endOf('day').format('YYYY-MM-DDTHH:mm:ss')

  const columns = useMemo<ColumnsType<SysOperLog>>(
    () => [
      { title: '模块', dataIndex: 'module', width: 130 },
      { title: '操作', dataIndex: 'operation', width: 160, ellipsis: true },
      { title: '操作人', dataIndex: 'operatorName', width: 120, render: (value) => value ?? '-' },
      {
        title: '请求方式',
        dataIndex: 'requestMethod',
        width: 100,
        render: (value) => value ? <Tag color={value === 'DELETE' ? 'red' : value === 'POST' ? 'blue' : 'green'}>{value}</Tag> : '-',
      },
      { title: '请求地址', dataIndex: 'requestUrl', minWidth: 220, ellipsis: true, render: (value) => value ?? '-' },
      {
        title: '结果',
        dataIndex: 'status',
        width: 90,
        render: (value: number) => (
          <StatusTag value={value === 1 ? '成功' : '失败'} semantic={value === 1 ? 'success' : 'error'} />
        ),
      },
      { title: '耗时', dataIndex: 'costMs', width: 90, render: (value) => value == null ? '-' : `${value} ms` },
      { title: '时间', dataIndex: 'createTime', width: 180 },
    ],
    [],
  )

  return (
    <div className="erp-page">
      <PageHeader title="操作日志" subtitle="查看系统管理及业务操作的审计记录" />
      <PageTable<SysOperLog>
        title="日志列表"
        url="/system/logs"
        queryKey={['system', 'logs', debouncedModule, debouncedOperator, status, beginTime, endTime]}
        params={{
          module: debouncedModule || undefined,
          operatorName: debouncedOperator || undefined,
          status,
          beginTime,
          endTime,
        }}
        columns={columns}
        toolbar={
          <Space wrap>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="搜索模块"
              value={module}
              onChange={(e) => setModule(e.target.value)}
              style={{ width: 200 }}
            />
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="搜索操作人"
              value={operatorName}
              onChange={(e) => setOperatorName(e.target.value)}
              style={{ width: 200 }}
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
