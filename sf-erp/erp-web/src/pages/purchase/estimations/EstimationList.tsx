import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import {
  estimationApi,
  type EstimationRow,
  type EstimationStatus,
} from '@/api/purchase'
import { fmtAmount, optionLabel, useSupplierOptions } from '../shared'

const { Text } = Typography

/** 暂估单状态 -> 标签（与后端 pur_estimation.status 注释一致） */
const STATUS_MAP: Record<EstimationStatus, { text: string; color: string }> = {
  0: { text: '暂估中', color: 'processing' },
  10: { text: '已冲回', color: 'success' },
  20: { text: '部分冲回', color: 'orange' },
}

const STATUS_OPTIONS = Object.entries(STATUS_MAP).map(([value, cfg]) => ({
  value: Number(value),
  label: cfg.text,
}))

function StatusTag({ status }: { status: EstimationStatus }) {
  const cfg = STATUS_MAP[status] ?? { text: `未知(${status})`, color: 'default' }
  return <Tag color={cfg.color}>{cfg.text}</Tag>
}

interface ReverseFormValues {
  invoiceNo: string
  actualAmount: number
}

/**
 * 采购暂估应付页（货到票未到，V231）：
 * 汇总卡片（当月暂估总额/已冲回/未冲回余额）+ 暂估单列表 + 手动冲回 Modal + 手工生成 Modal。
 * 自动链路（入库审核即暂估 / 进项发票认证后自动冲回）由后端扫描器与事件驱动，本页只做查询与补救操作。
 */
export default function EstimationList() {
  const [period, setPeriod] = useState<Dayjs | null>(dayjs())
  const [keyword, setKeyword] = useState('')
  const [supplierId, setSupplierId] = useState<number | undefined>()
  const [status, setStatus] = useState<number | undefined>()
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const [reverseTarget, setReverseTarget] = useState<EstimationRow | null>(null)
  const [createOpen, setCreateOpen] = useState(false)

  const [reverseForm] = Form.useForm<ReverseFormValues>()
  const [createForm] = Form.useForm<{ instockId: number }>()

  const suppliers = useSupplierOptions()
  const periodStr = period ? period.format('YYYYMM') : undefined

  const params = {
    pageNum,
    pageSize,
    keyword: keyword || undefined,
    supplierId,
    status,
    period: periodStr,
  }

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['purchase', 'estimation', params],
    queryFn: () => estimationApi.page(params),
  })

  const summary = useQuery({
    queryKey: ['purchase', 'estimation', 'summary', periodStr],
    queryFn: () => estimationApi.summary(periodStr),
  })

  const queryClient = useQueryClient()
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['purchase', 'estimation'] })
  }

  const reverseMut = useMutation({
    mutationFn: (v: { id: number; payload: ReverseFormValues }) =>
      estimationApi.reverse(v.id, v.payload),
    onSuccess: (_, v) => {
      message.success(`冲回成功：发票 ${v.payload.invoiceNo} 已入账（红字暂估+正式应付+差额行）`)
      setReverseTarget(null)
      reverseForm.resetFields()
      invalidate()
    },
  })

  const createMut = useMutation({
    mutationFn: (instockId: number) => estimationApi.createFromInstock(instockId),
    onSuccess: (id) => {
      message.success(`暂估生成成功（id=${id}），已写暂估应付`)
      setCreateOpen(false)
      createForm.resetFields()
      invalidate()
    },
  })

  const columns: ColumnsType<EstimationRow> = [
    { title: '暂估单号', dataIndex: 'estimationNo', width: 160 },
    { title: '入库单号', dataIndex: 'instockNo', width: 160 },
    { title: '供应商', dataIndex: 'supplierName', width: 170, ellipsis: true },
    {
      title: '暂估金额(不含税)',
      dataIndex: 'amount',
      width: 140,
      align: 'right',
      render: (v: number) => <Text strong>{fmtAmount(v)}</Text>,
    },
    {
      title: '已冲回',
      dataIndex: 'reversedAmount',
      width: 110,
      align: 'right',
      render: (v: number) => fmtAmount(v),
    },
    {
      title: '未冲回余额',
      dataIndex: 'openAmount',
      width: 110,
      align: 'right',
      render: (v: number) => <Text type={v > 0 ? 'warning' : undefined}>{fmtAmount(v)}</Text>,
    },
    { title: '状态', dataIndex: 'status', width: 100, render: (v: EstimationStatus) => <StatusTag status={v} /> },
    { title: '暂估期间', dataIndex: 'period', width: 90 },
    { title: '冲回发票号', dataIndex: 'invoiceNo', width: 120, render: (v: string | null) => v ?? '-' },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      fixed: 'right',
      render: (_, row) =>
        row.status !== 10 ? (
          <HasPerm code="purchase:estimation:audit">
            <Button
              size="small"
              type="link"
              onClick={() => {
                setReverseTarget(row)
                reverseForm.resetFields()
              }}
            >
              冲回
            </Button>
          </HasPerm>
        ) : (
          <Text type="secondary">-</Text>
        ),
    },
  ]

  const summaryCols: ColumnsType<NonNullable<typeof summary.data>['suppliers'][number]> = [
    { title: '供应商', dataIndex: 'supplierName', width: 180, ellipsis: true, render: (v: string | null, r) => v ?? `供应商 #${r.supplierId}` },
    { title: '暂估单数', dataIndex: 'estimationCount', width: 90, align: 'right' },
    { title: '暂估总额', dataIndex: 'totalAmount', width: 120, align: 'right', render: (v: number) => fmtAmount(v) },
    { title: '已冲回', dataIndex: 'reversedAmount', width: 120, align: 'right', render: (v: number) => fmtAmount(v) },
    { title: '未冲回余额', dataIndex: 'openAmount', width: 120, align: 'right', render: (v: number) => fmtAmount(v) },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card size="small">
        <Space wrap size={12}>
          <DatePicker
            picker="month"
            value={period}
            onChange={(v) => {
              setPeriod(v)
              setPageNum(1)
            }}
            placeholder="暂估期间"
            allowClear={false}
          />
          <Input.Search
            placeholder="暂估单号/入库单号/发票号"
            allowClear
            style={{ width: 240 }}
            onSearch={(v) => {
              setKeyword(v)
              setPageNum(1)
            }}
          />
          <Select
            placeholder="供应商"
            allowClear
            showSearch
            optionFilterProp="label"
            style={{ width: 200 }}
            value={supplierId}
            onChange={(v) => {
              setSupplierId(v)
              setPageNum(1)
            }}
            options={(suppliers.data ?? []).map((s) => ({ value: s.id, label: optionLabel(s) }))}
          />
          <Select
            placeholder="状态"
            allowClear
            style={{ width: 130 }}
            value={status}
            onChange={(v) => {
              setStatus(v)
              setPageNum(1)
            }}
            options={STATUS_OPTIONS}
          />
          <HasPerm code="purchase:estimation:edit">
            <Button type="primary" onClick={() => setCreateOpen(true)}>
              手工生成暂估
            </Button>
          </HasPerm>
        </Space>
      </Card>

      <Row gutter={16}>
        <Col span={8}>
          <Card size="small" loading={summary.isLoading}>
            <Statistic
              title={`当月暂估总额（${summary.data?.period ?? ''}）`}
              value={summary.data?.totalAmount ?? 0}
              precision={2}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" loading={summary.isLoading}>
            <Statistic
              title="已冲回金额"
              value={summary.data?.reversedAmount ?? 0}
              precision={2}
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" loading={summary.isLoading}>
            <Statistic
              title="暂估余额（未冲回）"
              value={summary.data?.openAmount ?? 0}
              precision={2}
              valueStyle={{ color: summary.data?.openAmount ? '#cf1322' : undefined }}
            />
          </Card>
        </Col>
      </Row>

      <Card size="small" title="供应商维度汇总" loading={summary.isLoading}>
        <Table
          size="small"
          rowKey="supplierId"
          columns={summaryCols}
          dataSource={summary.data?.suppliers ?? []}
          pagination={false}
        />
      </Card>

      <Card size="small" title="暂估单列表">
        <Table<EstimationRow>
          size="small"
          rowKey="id"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          scroll={{ x: 1280 }}
          pagination={{
            current: pageNum,
            pageSize,
            total: data?.total ?? 0,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPageNum(p)
              setPageSize(s)
            },
          }}
        />
      </Card>

      <Modal
        title={`暂估冲回（${reverseTarget?.estimationNo ?? ''}）`}
        open={reverseTarget != null}
        onCancel={() => setReverseTarget(null)}
        onOk={() =>
          reverseForm.validateFields().then((v) => {
            if (reverseTarget) reverseMut.mutate({ id: reverseTarget.id, payload: v })
          })
        }
        confirmLoading={reverseMut.isPending}
        destroyOnClose
      >
        {reverseTarget && (
          <Space direction="vertical" size={4} style={{ width: '100%', marginBottom: 12 }}>
            <Text>
              未冲回余额 <Text strong type="warning">{fmtAmount(reverseTarget.openAmount)}</Text>
              ；发票实际金额低于余额时按金额部分冲回。
            </Text>
          </Space>
        )}
        <Form form={reverseForm} layout="vertical">
          <Form.Item
            name="invoiceNo"
            label="发票号"
            rules={[{ required: true, message: '请输入发票号' }, { max: 20, message: '发票号长度不能超过 20' }]}
          >
            <Input placeholder="冲回所用进项发票号" maxLength={20} />
          </Form.Item>
          <Form.Item
            name="actualAmount"
            label="发票实际金额（价税合计）"
            rules={[{ required: true, message: '请输入发票实际金额' }]}
          >
            <InputNumber
              style={{ width: '100%' }}
              min={0.01}
              precision={2}
              placeholder="发票价税合计金额"
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="手工生成暂估（自动暂估漏单的补救入口）"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() =>
          createForm.validateFields().then((v) => createMut.mutate(v.instockId))
        }
        confirmLoading={createMut.isPending}
        destroyOnClose
      >
        <Space direction="vertical" size={4} style={{ width: '100%', marginBottom: 12 }}>
          <Text type="secondary">
            仅处理已审核且无生效进项发票、尚未暂估的入库单；暂估金额=明细数量×不含税单价。
          </Text>
        </Space>
        <Form form={createForm} layout="vertical">
          <Form.Item
            name="instockId"
            label="入库单 ID"
            rules={[{ required: true, message: '请输入采购入库单 ID' }]}
          >
            <InputNumber style={{ width: '100%' }} min={1} precision={0} placeholder="pur_instock.id" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
