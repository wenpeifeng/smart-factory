import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, DatePicker, Input, Select, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { Dayjs } from 'dayjs'
import { getFinanceBookId, pageReceivables, setFinanceBookId, type ReceivableVO, type SettleStatus } from '@/api/finance'
import BookSelect from '@/pages/finance/components/BookSelect'
import SettleModal from '@/pages/finance/components/SettleModal'

const { Title } = Typography

/** 核销状态 → 标签配色与文案 */
const STATUS_TAG: Record<SettleStatus, { color: string; text: string }> = {
  UNSETTLED: { color: 'error', text: '未核销' },
  PARTIAL: { color: 'warning', text: '部分核销' },
  SETTLED: { color: 'success', text: '已核销' },
}

/** 金额展示（右对齐 + 千分位由 toLocaleString 处理） */
const money = (v: number) => v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/**
 * 应收管理页：
 * 分页列表（客户/状态/月份/单号筛选）+ 收款核销弹窗。
 * 数据由销售出库自动生成（fin_receivable），本页负责核销与跟踪。
 */
export default function ReceivableList() {
  const queryClient = useQueryClient()
  const [bookId, setBookId] = useState<number>(getFinanceBookId())
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<SettleStatus | undefined>()
  const [month, setMonth] = useState<string | undefined>()
  const [settleFor, setSettleFor] = useState<number | null>(null)

  /** 页内切换账套：更新模块上下文（X-Book-Id）并失效全部财务缓存 */
  const switchBook = (id: number) => {
    setFinanceBookId(id)
    setBookId(id)
    setPageNum(1)
    queryClient.invalidateQueries({ queryKey: ['finance'] })
  }

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['finance', 'receivables', bookId, { pageNum, pageSize, keyword, status, month }],
    queryFn: () =>
      pageReceivables({
        pageNum,
        pageSize,
        keyword: keyword || undefined,
        status,
        month,
      }),
  })

  const columns: ColumnsType<ReceivableVO> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 150,
      render: (v: string | null, row) => v ?? `客户 #${row.customerId}`,
    },
    { title: '来源单号', dataIndex: 'bizNo', width: 170 },
    { title: '业务日期', dataIndex: 'bizDate', width: 110, render: (v: string | null) => v ?? '-' },
    { title: '应收金额', dataIndex: 'amount', align: 'right', width: 120, render: money },
    { title: '已核销', dataIndex: 'settledAmount', align: 'right', width: 110, render: money },
    {
      title: '未核销余额',
      dataIndex: 'unsettledAmount',
      align: 'right',
      width: 120,
      render: (v: number) => <span style={{ color: v > 0 ? '#cf1322' : undefined }}>{money(v)}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: SettleStatus) => <Tag color={STATUS_TAG[v].color}>{STATUS_TAG[v].text}</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 110,
      render: (_, row) => (
        <Button
          type="link"
          size="small"
          disabled={row.status === 'SETTLED'}
          onClick={() => setSettleFor(row.customerId)}
        >
          收款核销
        </Button>
      ),
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        应收管理
      </Title>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <span style={{ color: '#666' }}>账套：</span>
          <BookSelect value={bookId} onChange={switchBook} />
          <Input.Search
            placeholder="来源单号模糊搜索"
            style={{ width: 220 }}
            allowClear
            onSearch={setKeyword}
          />
          <Select<SettleStatus>
            style={{ width: 140 }}
            placeholder="核销状态"
            allowClear
            value={status}
            onChange={(v) => {
              setStatus(v)
              setPageNum(1)
            }}
            options={[
              { value: 'UNSETTLED', label: '未核销' },
              { value: 'PARTIAL', label: '部分核销' },
              { value: 'SETTLED', label: '已核销' },
            ]}
          />
          <DatePicker.MonthPicker
            placeholder="业务月份"
            onChange={(d: Dayjs | null) => {
              setMonth(d ? d.format('YYYY-MM') : undefined)
              setPageNum(1)
            }}
          />
        </Space>

        <Table<ReceivableVO>
          rowKey="id"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
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

      <SettleModal
        open={settleFor != null}
        kind="receipt"
        partnerId={settleFor}
        onClose={() => setSettleFor(null)}
      />
    </div>
  )
}
