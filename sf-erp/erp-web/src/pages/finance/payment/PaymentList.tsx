import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, Input, Modal, Space, Table, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { getFinanceBookId, pagePayments, setFinanceBookId, type PaymentVO } from '@/api/finance'
import BookSelect from '@/pages/finance/components/BookSelect'
import HasPerm from '@/components/HasPerm'
import { reverseDoc } from '@/api/basedata'

const { Title } = Typography

const money = (v: number) => v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/**
 * 付款单列表页：核销应付产生的付款单跟踪 + 已审单据通用红冲。
 * 付款单无独立状态列（核销即生效），红冲防重由后端 biz_reverse_log 台账保证。
 */
export default function PaymentList() {
  const queryClient = useQueryClient()
  const [bookId, setBookId] = useState<number>(getFinanceBookId())
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [keyword, setKeyword] = useState('')

  const { data, isFetching } = useQuery({
    queryKey: ['finance', 'payments', bookId, pageNum, pageSize, keyword],
    queryFn: () => pagePayments({ pageNum, pageSize, keyword: keyword || undefined }),
  })

  const switchBook = (id: number) => {
    setFinanceBookId(id)
    setBookId(id)
    setPageNum(1)
  }

  /** 红冲：负数反向单 + 核销回冲 + 账户余额/流水反向，不可撤销 */
  const onReverse = (row: PaymentVO) => {
    let reason = ''
    Modal.confirm({
      title: `红冲付款单 ${row.paymentNo}`,
      content: (
        <div>
          <p>将生成负数反向付款单并回冲核销与账户余额，操作不可撤销。</p>
          <Input placeholder="红冲原因（选填）" onChange={(e) => (reason = e.target.value)} />
        </div>
      ),
      okText: '红冲',
      okButtonProps: { danger: true },
      onOk: async () => {
        const r = await reverseDoc('PAYMENT', row.id, reason || undefined)
        message.success(`已红冲，反向单号 ${r.reverseDocNo}`)
        queryClient.invalidateQueries({ queryKey: ['finance', 'payments'] })
      },
    })
  }

  const columns: ColumnsType<PaymentVO> = [
    { title: '付款单号', dataIndex: 'paymentNo', width: 170 },
    { title: '供应商', dataIndex: 'supplierName', width: 160, render: (v) => v ?? '-' },
    { title: '付款账户', dataIndex: 'accountName', width: 130, render: (v) => v ?? '-' },
    { title: '业务日期', dataIndex: 'bizDate', width: 110 },
    { title: '金额', dataIndex: 'amount', align: 'right', width: 120, render: money },
    { title: '已核销', dataIndex: 'verifiedAmount', align: 'right', width: 120, render: money },
    {
      title: '核销状态',
      key: 'settle',
      width: 100,
      render: (_, row) =>
        row.verifiedAmount >= row.amount ? (
          <Tag color="success">已核销</Tag>
        ) : row.verifiedAmount > 0 ? (
          <Tag color="warning">部分核销</Tag>
        ) : (
          <Tag>未核销</Tag>
        ),
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v) => v ?? '-' },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, row) => (
        <Space>
          <HasPerm code="finance:payment:reverse">
            <Button size="small" danger onClick={() => onReverse(row)}>
              红冲
            </Button>
          </HasPerm>
        </Space>
      ),
    },
  ]

  return (
    <Card>
      <Title level={4} style={{ marginTop: 0 }}>
        付款单 <BookSelect value={bookId} onChange={switchBook} />
      </Title>
      <Space style={{ marginBottom: 16 }}>
        <Input.Search
          placeholder="单号搜索"
          allowClear
          onSearch={(v) => {
            setKeyword(v)
            setPageNum(1)
          }}
          style={{ width: 240 }}
        />
      </Space>
      <Table<PaymentVO>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isFetching}
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
    </Card>
  )
}
