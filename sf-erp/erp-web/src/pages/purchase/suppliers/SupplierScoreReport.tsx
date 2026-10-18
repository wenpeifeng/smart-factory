import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, Col, Input, Popconfirm, Row, Select, Space, Statistic, Table, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { SyncOutlined } from '@ant-design/icons'
import { supplierScoreApi, type SupplierScoreRow } from '@/api/purchase'

const { Title, Text } = Typography

const GRADE_COLOR: Record<string, string> = { A: 'green', B: 'blue', C: 'orange', D: 'red' }

/** 周期类型选项 */
const PERIOD_TYPES = [
  { value: 'MONTH', label: '月度' },
  { value: 'QUARTER', label: '季度' },
]

/**
 * 供应商评级报表页（V141）。
 * 汇总卡片（等级分布/均分/交易额）+ 评分明细表；支持手工重算。
 */
export default function SupplierScoreReport() {
  const queryClient = useQueryClient()

  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [periodType, setPeriodType] = useState<string>('MONTH')
  const [period, setPeriod] = useState('')
  const [grade, setGrade] = useState<string | undefined>(undefined)

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['purchase', 'supplier-score', pageNum, pageSize, periodType, period, grade],
    queryFn: () =>
      supplierScoreApi.page({
        pageNum,
        pageSize,
        periodType,
        period: period || undefined,
        grade,
      }),
  })

  const { data: summary } = useQuery({
    queryKey: ['purchase', 'supplier-score', 'summary', periodType, period],
    queryFn: () => supplierScoreApi.summary(periodType, period || undefined),
  })

  const recalcMutation = useMutation({
    mutationFn: () => supplierScoreApi.recalc(periodType, period || undefined),
    onSuccess: (count) => {
      message.success(`重算完成，参与评分供应商 ${count} 家`)
      queryClient.invalidateQueries({ queryKey: ['purchase', 'supplier-score'] })
    },
  })

  const columns: ColumnsType<SupplierScoreRow> = [
    { title: '供应商编码', dataIndex: 'supplierCode', width: 120 },
    { title: '供应商名称', dataIndex: 'supplierName', ellipsis: true },
    { title: '周期', dataIndex: 'period', width: 90 },
    { title: '订单数', dataIndex: 'orderCount', width: 80, align: 'right' },
    {
      title: '交易额',
      dataIndex: 'orderAmount',
      width: 120,
      align: 'right',
      render: (v: number) => v?.toLocaleString?.() ?? v,
    },
    {
      title: '准时交货率',
      dataIndex: 'ontimeRate',
      width: 120,
      align: 'right',
      render: (v: number) => `${v ?? 0}%`,
    },
    {
      title: '来料合格率',
      dataIndex: 'qualifiedRate',
      width: 120,
      align: 'right',
      render: (v: number) => `${v ?? 0}%`,
    },
    {
      title: '退货率',
      dataIndex: 'returnRate',
      width: 90,
      align: 'right',
      render: (v: number) => `${v ?? 0}%`,
    },
    {
      title: '总分',
      dataIndex: 'totalScore',
      width: 80,
      align: 'right',
    },
    {
      title: '等级',
      dataIndex: 'grade',
      width: 70,
      render: (v: string) => <Tag color={GRADE_COLOR[v] ?? 'default'}>{v}</Tag>,
    },
  ]

  return (
    <Card>
      <Title level={4}>供应商评级</Title>
      <Text type="secondary">
        总分 = 100 × (30%×准时交货率 + 40%×来料合格率 + 30%×(1-退货率))；等级 A≥90 / B≥75 / C≥60 / D&lt;60。
        每月 1 日自动计算上月（季末月另算上季度），也可手工重算。
      </Text>

      <Row gutter={16} style={{ marginTop: 16, marginBottom: 16 }}>
        <Col span={3}>
          <Statistic title="供应商数" value={summary?.supplierCount ?? 0} />
        </Col>
        <Col span={3}>
          <Statistic title="A 级" value={summary?.gradeA ?? 0} valueStyle={{ color: '#52c41a' }} />
        </Col>
        <Col span={3}>
          <Statistic title="B 级" value={summary?.gradeB ?? 0} valueStyle={{ color: '#1677ff' }} />
        </Col>
        <Col span={3}>
          <Statistic title="C 级" value={summary?.gradeC ?? 0} valueStyle={{ color: '#fa8c16' }} />
        </Col>
        <Col span={3}>
          <Statistic title="D 级" value={summary?.gradeD ?? 0} valueStyle={{ color: '#ff4d4f' }} />
        </Col>
        <Col span={4}>
          <Statistic title="平均总分" value={summary?.avgScore ?? 0} precision={2} />
        </Col>
        <Col span={5}>
          <Statistic title="周期交易额合计" value={summary?.totalOrderAmount ?? 0} precision={2} />
        </Col>
      </Row>

      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          value={periodType}
          style={{ width: 110 }}
          onChange={(v) => {
            setPeriodType(v)
            setPageNum(1)
          }}
          options={PERIOD_TYPES}
        />
        <Input
          placeholder={periodType === 'QUARTER' ? '周期 yyyy-Qn（空=最近）' : '周期 yyyy-MM（空=最近）'}
          style={{ width: 220 }}
          value={period}
          onChange={(e) => setPeriod(e.target.value)}
          allowClear
        />
        <Select
          placeholder="等级"
          allowClear
          style={{ width: 90 }}
          value={grade}
          onChange={(v) => {
            setGrade(v)
            setPageNum(1)
          }}
          options={['A', 'B', 'C', 'D'].map((g) => ({ value: g, label: `${g} 级` }))}
        />
        <HasPerm code="purchase:supplierScore:recalc">
          <Popconfirm
            title={`重算${period || '最近已完结'}周期的全部供应商评分？`}
            onConfirm={() => recalcMutation.mutate()}
          >
            <Button icon={<SyncOutlined />} loading={recalcMutation.isPending}>
              手工重算
            </Button>
          </Popconfirm>
        </HasPerm>
      </Space>

      <Table<SupplierScoreRow>
        className="erp-nowrap-table"
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading || isFetching}
        tableLayout="fixed"
        scroll={{ x: 1120 }}
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
