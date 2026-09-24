import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Input,
  Modal,
  Popconfirm,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import {
  pageDepreciations,
  getDepreciationDetail,
  runDepreciation,
  getStatistics,
  type DepreciationPeriodVO,
  type DepreciationDetailVO,
  type StatisticsRowVO,
} from '@/api/asset'

const { Title } = Typography

/** 期间 yyyy-MM 校验（提交时转 yyyyMM） */
function toPeriod(value: Dayjs | null): string | undefined {
  return value ? value.format('YYYYMM') : undefined
}

/** 统计行表格列（部门/类别共用） */
const statColumns = (dimension: string): ColumnsType<StatisticsRowVO> => [
  { title: dimension, dataIndex: 'dimensionName', width: 130 },
  {
    title: '原值',
    dataIndex: 'originalValue',
    align: 'right',
    render: (v: number) => v.toLocaleString('zh-CN', { minimumFractionDigits: 2 }),
  },
  {
    title: '累计折旧',
    dataIndex: 'accumulatedDepreciation',
    align: 'right',
    render: (v: number) => v.toLocaleString('zh-CN', { minimumFractionDigits: 2 }),
  },
  {
    title: '净值',
    dataIndex: 'netValue',
    align: 'right',
    render: (v: number) => v.toLocaleString('zh-CN', { minimumFractionDigits: 2 }),
  },
]

/**
 * 折旧管理页：期间列表（运行状态/凭证回显）、执行计提（重复运行后端拒绝）、
 * 期间明细表、部门/类别维度统计（截至期间的原值/累计折旧/净值）。
 */
export default function DepreciationPage() {
  const queryClient = useQueryClient()
  const { modal } = App.useApp()

  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [runMonth, setRunMonth] = useState<Dayjs>(dayjs())
  const [detailPeriod, setDetailPeriod] = useState<string | null>(null)
  const [statMonth, setStatMonth] = useState<Dayjs>(dayjs())

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['asset', 'depreciations', pageNum, pageSize],
    queryFn: () => pageDepreciations({ pageNum, pageSize }),
  })

  const { data: stat, isLoading: statLoading } = useQuery({
    queryKey: ['asset', 'statistics', statMonth.format('YYYYMM')],
    queryFn: () => getStatistics(statMonth.format('YYYYMM')),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['asset', 'depreciations'] })

  const runMutation = useMutation({
    mutationFn: (period: string) => runDepreciation(period),
    onSuccess: (result) => {
      if (result.assetCount > 0) {
        modal.success({
          title: `期间 ${result.period} 计提完成`,
          content: `共 ${result.assetCount} 项资产，合计 ${result.totalAmount.toLocaleString('zh-CN', {
            minimumFractionDigits: 2,
          })}，汇总凭证 ${result.voucherNo ?? result.voucherId}`,
        })
      } else {
        modal.info({ title: `期间 ${result.period} 无可计提资产`, content: result.message ?? '' })
      }
      invalidate()
      queryClient.invalidateQueries({ queryKey: ['asset', 'statistics'] })
    },
  })

  const periodColumns: ColumnsType<DepreciationPeriodVO> = [
    { title: '期间', dataIndex: 'period', width: 100 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: string) =>
        s === 'CLOSED' ? <Tag color="green">已计提</Tag> : <Tag color="blue">开放</Tag>,
    },
    { title: '资产数', dataIndex: 'assetCount', width: 90, align: 'right' },
    {
      title: '计提总额',
      dataIndex: 'totalAmount',
      width: 120,
      align: 'right',
      render: (v: number) => v.toLocaleString('zh-CN', { minimumFractionDigits: 2 }),
    },
    { title: '汇总凭证', dataIndex: 'voucherNo', width: 150, render: (v) => v ?? '-' },
    {
      title: '操作',
      key: 'action',
      width: 110,
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => setDetailPeriod(record.period)}>
          查看明细
        </Button>
      ),
    },
  ]

  const detailColumns: ColumnsType<DepreciationDetailVO> = [
    { title: '资产编码', dataIndex: 'assetCode', width: 100 },
    { title: '资产名称', dataIndex: 'assetName', width: 180, ellipsis: true },
    { title: '类别', dataIndex: 'categoryName', width: 110, render: (v) => v ?? '-' },
    { title: '部门', dataIndex: 'deptName', width: 110, render: (v) => v ?? '-' },
    {
      title: '本月折旧额',
      dataIndex: 'monthAmount',
      width: 120,
      align: 'right',
      render: (v: number) => v.toLocaleString('zh-CN', { minimumFractionDigits: 2 }),
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        折旧管理
      </Title>

      {/* 执行计提 + 期间列表 */}
      <Card style={{ marginBottom: 16 }}>
        <Space style={{ marginBottom: 16 }}>
          <Input.Group compact>
            <DatePicker
              picker="month"
              value={runMonth}
              onChange={(v) => setRunMonth(v ?? dayjs())}
              style={{ width: 140 }}
            />
            <Popconfirm
              title={`确认执行期间 ${runMonth.format('YYYYMM')} 折旧计提？`}
              description="在用且未提满的资产按（原值-残值)/年限月 计提，生成汇总凭证；同一期间只能运行一次。"
              onConfirm={() => runMutation.mutate(toPeriod(runMonth) ?? dayjs().format('YYYYMM'))}
            >
              <Button type="primary" loading={runMutation.isPending}>
                运行计提
              </Button>
            </Popconfirm>
          </Input.Group>
        </Space>
        <Table<DepreciationPeriodVO>
          rowKey="id"
          columns={periodColumns}
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

      {/* 统计（截至期间，部门/类别维度） */}
      <Card
        title={
          <Space>
            <span>资产统计（截至期间）</span>
            <DatePicker
              picker="month"
              size="small"
              value={statMonth}
              onChange={(v) => setStatMonth(v ?? dayjs())}
            />
          </Space>
        }
      >
        <Row gutter={16} style={{ marginBottom: 16 }}>
          <Col span={8}>
            <Statistic
              title="原值合计"
              value={stat?.total.originalValue ?? 0}
              precision={2}
              loading={statLoading}
            />
          </Col>
          <Col span={8}>
            <Statistic
              title="累计折旧合计"
              value={stat?.total.accumulatedDepreciation ?? 0}
              precision={2}
              loading={statLoading}
            />
          </Col>
          <Col span={8}>
            <Statistic
              title="净值合计"
              value={stat?.total.netValue ?? 0}
              precision={2}
              loading={statLoading}
            />
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={12}>
            <Table<StatisticsRowVO>
              rowKey={(r) => `dept-${r.dimensionId ?? 'none'}`}
              columns={statColumns('部门')}
              dataSource={stat?.byDept ?? []}
              loading={statLoading}
              size="small"
              pagination={false}
              locale={{ emptyText: '该期间无在册资产' }}
            />
          </Col>
          <Col span={12}>
            <Table<StatisticsRowVO>
              rowKey={(r) => `cat-${r.dimensionId ?? 'none'}`}
              columns={statColumns('类别')}
              dataSource={stat?.byCategory ?? []}
              loading={statLoading}
              size="small"
              pagination={false}
              locale={{ emptyText: '该期间无在册资产' }}
            />
          </Col>
        </Row>
      </Card>

      {/* 期间明细弹窗 */}
      <Modal
        title={`期间明细 - ${detailPeriod ?? ''}`}
        open={!!detailPeriod}
        onCancel={() => setDetailPeriod(null)}
        footer={null}
        width={760}
        destroyOnClose
      >
        <PeriodDetail period={detailPeriod} columns={detailColumns} />
      </Modal>
    </div>
  )
}

/** 期间明细内容（独立组件：弹窗打开时才发起请求） */
function PeriodDetail({
  period,
  columns,
}: {
  period: string | null
  columns: ColumnsType<DepreciationDetailVO>
}) {
  const { data, isLoading } = useQuery({
    queryKey: ['asset', 'depreciations', 'detail', period],
    queryFn: () => getDepreciationDetail(period ?? ''),
    enabled: !!period,
  })
  return (
    <Table<DepreciationDetailVO>
      rowKey="id"
      columns={columns}
      dataSource={data?.details ?? []}
      loading={isLoading}
      size="small"
      pagination={false}
      summary={() =>
        data && data.details.length > 0 ? (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0}>合计（{data.assetCount} 项）</Table.Summary.Cell>
            <Table.Summary.Cell index={1} colSpan={3} />
            <Table.Summary.Cell index={4} align="right">
              <Typography.Text strong>
                {data.totalAmount.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}
              </Typography.Text>
            </Table.Summary.Cell>
          </Table.Summary.Row>
        ) : null
      }
    />
  )
}
