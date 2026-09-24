import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Card, DatePicker, Popconfirm, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import SummaryTable from '@/pages/cost/components/SummaryTable'
import {
  fetchPeriods,
  fetchSettleSummary,
  fmtAmount,
  fmtPeriod,
  settlePeriod,
  type CostPeriodRow,
} from '@/api/cost'

const { Title, Text } = Typography

/**
 * 月末核算页：期间列表 + 收发存汇总表 + 结账操作。
 * 结账自动生成转账凭证（借主营业务成本/生产成本，贷库存商品）并关闭期间。
 */
export default function SettlePage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [period, setPeriod] = useState<string>(dayjs().format('YYYYMM'))
  const [settleTarget, setSettleTarget] = useState<Dayjs | null>(null)

  const { data: periods, isLoading: periodsLoading } = useQuery({
    queryKey: ['cost', 'periods'],
    queryFn: fetchPeriods,
  })
  const { data: summary, isLoading: summaryLoading } = useQuery({
    queryKey: ['cost', 'settle-summary', period],
    queryFn: () => fetchSettleSummary(period),
  })

  const settleMutation = useMutation({
    mutationFn: (p: string) => settlePeriod(p),
    onSuccess: (r) => {
      message.success(
        r.voucherNo
          ? `期间 ${fmtPeriod(r.period)} 结账完成：出库成本 ${fmtAmount(r.outAmount)}，已生成凭证 ${r.voucherNo}（草稿）`
          : `期间 ${fmtPeriod(r.period)} 结账完成：本期无出库成本，未生成凭证`,
      )
      setSettleTarget(null)
      queryClient.invalidateQueries({ queryKey: ['cost'] })
    },
  })

  const periodColumns: ColumnsType<CostPeriodRow> = [
    { title: '期间', dataIndex: 'period', width: 100, render: fmtPeriod },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (s: string) =>
        s === 'CLOSED' ? <Tag color="green">已结账</Tag> : <Tag color="blue">打开</Tag>,
    },
    { title: '出库成本合计', dataIndex: 'outAmount', align: 'right', width: 130, render: fmtAmount },
    { title: '结账凭证', dataIndex: 'voucherNo', width: 150, render: (v) => v ?? '-' },
    { title: '结账时间', dataIndex: 'settleTime', width: 165, render: (v) => v ?? '-' },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        月末核算
      </Title>

      <Card size="small" style={{ marginBottom: 16 }}>
        <Space size={16} wrap>
          <DatePicker
            picker="month"
            value={settleTarget}
            onChange={setSettleTarget}
            placeholder="选择要结账的期间"
          />
          <Popconfirm
            title={`确认结账期间 ${settleTarget ? settleTarget.format('YYYY-MM') : ''}？`}
            description="结账后将生成转账凭证并关闭该期间，期间内不能再过账计价流水。"
            disabled={!settleTarget}
            onConfirm={() => settleTarget && settleMutation.mutate(settleTarget.format('YYYYMM'))}
          >
            <Button type="primary" danger disabled={!settleTarget} loading={settleMutation.isPending}>
              月末结账
            </Button>
          </Popconfirm>
          <Text type="secondary">
            结账 = 汇总本期出库成本 → 自动生成转账凭证（借 6401 主营业务成本 / 5001 生产成本，贷 1405
            库存商品，草稿待复核）→ 期间 CLOSED
          </Text>
        </Space>
      </Card>

      <Card title={`核算期间`} size="small" style={{ marginBottom: 16 }}>
        <Table<CostPeriodRow>
          rowKey="id"
          columns={periodColumns}
          dataSource={periods ?? []}
          loading={periodsLoading}
          size="small"
          pagination={false}
          onRow={(record) => ({
            onClick: () => setPeriod(record.period),
            style: { cursor: 'pointer', background: record.period === period ? '#e6f4ff' : undefined },
          })}
          locale={{ emptyText: '暂无结账记录（选择上方期间执行首次结账）' }}
        />
      </Card>

      <Card
        title={
          <Space>
            <span>收发存汇总</span>
            <DatePicker
              picker="month"
              value={dayjs(period, 'YYYYMM')}
              onChange={(d) => d && setPeriod(d.format('YYYYMM'))}
              allowClear={false}
              size="small"
            />
          </Space>
        }
        size="small"
      >
        <SummaryTable summary={summary} loading={summaryLoading} />
      </Card>
    </div>
  )
}
