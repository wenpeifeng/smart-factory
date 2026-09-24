import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, Popconfirm, Space, Table, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useState } from 'react'
import {
  closePeriod,
  getPeriods,
  getFinanceBookId,
  reopenPeriod,
  setFinanceBookId,
  type PeriodVO,
} from '@/api/finance'
import BookSelect from '@/pages/finance/components/BookSelect'

const { Title, Text } = Typography

/** 期间 yyyyMM → 展示 yyyy-MM */
const formatPeriod = (period: string) =>
  period.length === 6 ? `${period.slice(0, 4)}-${period.slice(4)}` : period

/**
 * 会计期间页（总账期间结账/反结账，V521）：
 * - 期间 = 凭证日期所在自然月，按账套隔离；
 * - 结账要求该期凭证全部过账（DRAFT 存在时后端拒绝并提示数量）；
 * - 已结账期间禁止该期凭证新增/修改/过账/删除（后端 FinVoucherService 四入口校验）。
 */
export default function PeriodList() {
  const queryClient = useQueryClient()
  const [currentBook, setCurrentBook] = useState<number>(getFinanceBookId())

  const { data: periods, isLoading, isFetching } = useQuery({
    queryKey: ['finance', 'periods', currentBook],
    queryFn: getPeriods,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['finance', 'periods'] })

  const closeMutation = useMutation({
    mutationFn: closePeriod,
    onSuccess: () => {
      message.success('期间已结账：该期凭证将被锁定（禁增删改/过账）')
      invalidate()
    },
  })

  const reopenMutation = useMutation({
    mutationFn: reopenPeriod,
    onSuccess: () => {
      message.success('期间已反结账，可继续调整该期凭证')
      invalidate()
    },
  })

  const switchBook = (bookId: number) => {
    // 与 BookList 相同约定：更新模块级账套上下文 + 失效全部财务查询缓存
    setFinanceBookId(bookId)
    setCurrentBook(bookId)
    queryClient.invalidateQueries({ queryKey: ['finance'] })
  }

  const columns: ColumnsType<PeriodVO> = [
    {
      title: '会计期间',
      dataIndex: 'period',
      width: 120,
      render: (v: string) => <Text strong>{formatPeriod(v)}</Text>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: PeriodVO['status']) =>
        v === 'CLOSED' ? <Tag color="error">已结账</Tag> : <Tag color="success">开启</Tag>,
    },
    {
      title: '凭证数',
      dataIndex: 'voucherCount',
      width: 100,
      align: 'right',
    },
    {
      title: '未过账（DRAFT）',
      dataIndex: 'draftCount',
      width: 140,
      align: 'right',
      render: (v: number) =>
        v > 0 ? <Text type="warning" strong>{v}</Text> : <Text type="secondary">0</Text>,
    },
    {
      title: '已过账（POSTED）',
      dataIndex: 'postedCount',
      width: 150,
      align: 'right',
    },
    {
      title: '结账时间',
      dataIndex: 'closeTime',
      width: 180,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: '反结账时间',
      dataIndex: 'reopenTime',
      width: 180,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, row) =>
        row.status === 'CLOSED' ? (
          <Popconfirm
            title="确认反结账？"
            description="反结账后该期凭证可继续修改/删除/过账"
            onConfirm={() => reopenMutation.mutate(row.period)}
          >
            <Button
              type="link"
              size="small"
              loading={reopenMutation.isPending && reopenMutation.variables === row.period}
            >
              反结账
            </Button>
          </Popconfirm>
        ) : (
          <Popconfirm
            title={`确认结账 ${formatPeriod(row.period)}？`}
            description={
              row.draftCount > 0
                ? `该期还有 ${row.draftCount} 张未过账凭证，结账将被拒绝`
                : '结账后该期凭证将被锁定，禁止新增/修改/过账/删除'
            }
            onConfirm={() => closeMutation.mutate(row.period)}
          >
            <Button
              type="link"
              size="small"
              disabled={row.voucherCount === 0}
              loading={closeMutation.isPending && closeMutation.variables === row.period}
            >
              结账
            </Button>
          </Popconfirm>
        ),
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        会计期间
      </Title>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <span style={{ color: '#666' }}>当前账套：</span>
          <BookSelect value={currentBook} onChange={switchBook} />
          <Text type="secondary" style={{ fontSize: 12 }}>
            期间 = 凭证日期所在自然月；结账要求该期凭证全部过账；已结账期间禁止该期凭证的
            新增 / 修改 / 过账 / 删除（反结账后恢复）
          </Text>
        </Space>

        <Table<PeriodVO>
          rowKey="period"
          columns={columns}
          dataSource={periods ?? []}
          loading={isLoading || isFetching}
          pagination={false}
        />
      </Card>
    </div>
  )
}
