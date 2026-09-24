import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  piecePriceApi,
  pieceWageApi,
  supportApi,
  type MaterialBrief,
  type PiecePriceRow,
  type PieceWageDetailRow,
  type PieceWageEmployeeSummaryRow,
  type PieceWageRow,
} from '@/api/hr'

const { Title, Text } = Typography

/**
 * 计件工资页：期间列表（归集/结账/明细/按员工合计）+ 计件价目维护。
 * 归集口径：完工订单数量 × 物料首条启用价目，员工归属工单制单人。
 */
export default function PieceWagePage() {
  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        计件工资
      </Title>
      <Tabs
        items={[
          { key: 'periods', label: '计件期间', children: <PeriodsTab /> },
          { key: 'prices', label: '计件价目', children: <PricesTab /> },
        ]}
      />
    </div>
  )
}

// ==================== 计件期间 Tab ====================

function PeriodsTab() {
  const queryClient = useQueryClient()
  const { message, modal } = App.useApp()

  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [collectMonth, setCollectMonth] = useState<dayjs.Dayjs>(dayjs())

  // 明细抽屉
  const [detailPeriod, setDetailPeriod] = useState<string | null>(null)
  const [detailPageNum, setDetailPageNum] = useState(1)
  const [detailEmployeeId, setDetailEmployeeId] = useState<number | undefined>()

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['hr', 'piece-wages', pageNum, pageSize],
    queryFn: () => pieceWageApi.page({ pageNum, pageSize }),
  })

  const { data: employees } = useQuery({
    queryKey: ['hr', 'employee-options'],
    queryFn: () => supportApi.employees(),
  })

  const { data: detailData, isLoading: detailLoading } = useQuery({
    queryKey: ['hr', 'piece-wage-details', detailPeriod, detailEmployeeId, detailPageNum],
    queryFn: () =>
      pieceWageApi.details(detailPeriod as string, {
        pageNum: detailPageNum,
        pageSize: 10,
        employeeId: detailEmployeeId,
      }),
    enabled: !!detailPeriod,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['hr', 'piece-wages'] })

  const collectMutation = useMutation({
    mutationFn: pieceWageApi.collect,
    onSuccess: (res) => {
      message.success(
        `归集完成：候选订单 ${res.candidateOrders} 单，生成明细 ${res.detailCount} 条，` +
          `金额合计 ${res.totalAmount}` +
          (res.skippedNoPrice + res.skippedNoEmployee > 0
            ? `（跳过：无价目 ${res.skippedNoPrice}、无归属员工 ${res.skippedNoEmployee}）`
            : ''),
        5,
      )
      invalidate()
      queryClient.invalidateQueries({ queryKey: ['hr', 'piece-wage-details'] })
    },
  })

  const closeMutation = useMutation({
    mutationFn: pieceWageApi.close,
    onSuccess: () => {
      message.success('期间已结账')
      invalidate()
    },
  })

  const showSummary = async (period: string) => {
    const rows = await pieceWageApi.employeeSummary(period)
    const columns: ColumnsType<PieceWageEmployeeSummaryRow> = [
      { title: '工号', dataIndex: 'empNo', width: 150, render: (v) => v ?? '-' },
      { title: '姓名', dataIndex: 'employeeName', width: 110, render: (v) => v ?? '-' },
      { title: '明细条数', dataIndex: 'detailCount', width: 100, align: 'right' },
      { title: '完成数量合计', dataIndex: 'totalQty', width: 130, align: 'right' },
      { title: '计件金额合计（元）', dataIndex: 'totalAmount', width: 150, align: 'right' },
    ]
    modal.info({
      title: `按员工合计 - ${period}`,
      width: 700,
      content: (
        <Table<PieceWageEmployeeSummaryRow>
          rowKey="employeeId"
          size="small"
          columns={columns}
          dataSource={rows}
          pagination={false}
          style={{ marginTop: 12 }}
        />
      ),
    })
  }

  const periodColumns: ColumnsType<PieceWageRow> = [
    { title: '期间', dataIndex: 'period', width: 100 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) =>
        v === 'OPEN' ? <Tag color="processing">开账</Tag> : <Tag color="default">已结账</Tag>,
    },
    { title: '明细条数', dataIndex: 'detailCount', width: 100, align: 'right' },
    { title: '金额合计（元）', dataIndex: 'totalAmount', width: 130, align: 'right' },
    { title: '开账时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_, record) => (
        <Space size={4}>
          {record.status === 'OPEN' && (
            <Popconfirm
              title="重新归集将清空本期间明细后重建，确认执行？"
              onConfirm={() => collectMutation.mutate(record.period)}
            >
              <Button type="link" size="small">
                归集
              </Button>
            </Popconfirm>
          )}
          <Button
            type="link"
            size="small"
            onClick={() => {
              setDetailPeriod(record.period)
              setDetailPageNum(1)
              setDetailEmployeeId(undefined)
            }}
          >
            明细
          </Button>
          <Button type="link" size="small" onClick={() => showSummary(record.period)}>
            按员工合计
          </Button>
          {record.status === 'OPEN' && (
            <Popconfirm
              title="结账后不可再归集，确认结账？"
              onConfirm={() => closeMutation.mutate(record.period)}
            >
              <Button type="link" size="small" danger>
                结账
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  const detailColumns: ColumnsType<PieceWageDetailRow> = [
    { title: '工号', dataIndex: 'empNo', width: 150, render: (v) => v ?? '-' },
    { title: '姓名', dataIndex: 'employeeName', width: 90, render: (v) => v ?? '-' },
    { title: '生产订单', dataIndex: 'workOrderNo', width: 160 },
    {
      title: '物料',
      key: 'material',
      width: 180,
      ellipsis: true,
      render: (_, r) => `${r.materialCode ?? '-'} / ${r.materialName ?? '-'} `,
    },
    { title: '工序', dataIndex: 'processName', width: 100 },
    { title: '完成数量', dataIndex: 'qty', width: 100, align: 'right' },
    { title: '单价（元）', dataIndex: 'unitPrice', width: 100, align: 'right' },
    { title: '金额（元）', dataIndex: 'amount', width: 110, align: 'right' },
    { title: '归集时间', dataIndex: 'createTime', width: 170 },
  ]

  return (
    <Card>
      <Space style={{ marginBottom: 16 }} wrap>
        <DatePicker
          picker="month"
          value={collectMonth}
          onChange={(v) => v && setCollectMonth(v)}
          allowClear={false}
        />
        <Popconfirm
          title={`归集 ${collectMonth.format('YYYY-MM')} 期间计件工资？`}
          onConfirm={() => collectMutation.mutate(collectMonth.format('YYYY-MM'))}
        >
          <Button type="primary" loading={collectMutation.isPending}>
            归集所选月份
          </Button>
        </Popconfirm>
        <Text type="secondary">
          归集口径：当月完工订单（已完工/已关闭）数量 × 物料首条启用价目；期间不存在时自动开账
        </Text>
      </Space>

      <Table<PieceWageRow>
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

      {/* 期间明细抽屉 */}
      <Drawer
        title={`计件明细 - ${detailPeriod ?? ''}`}
        open={!!detailPeriod}
        onClose={() => setDetailPeriod(null)}
        width={960}
        destroyOnClose
      >
        <Space style={{ marginBottom: 12 }}>
          <Select
            placeholder="员工筛选"
            style={{ width: 220 }}
            allowClear
            showSearch
            optionFilterProp="label"
            options={(employees ?? []).map((e) => ({ label: `${e.empNo} ${e.name}`, value: e.id }))}
            value={detailEmployeeId}
            onChange={(v) => {
              setDetailPageNum(1)
              setDetailEmployeeId(v)
            }}
          />
        </Space>
        <Table<PieceWageDetailRow>
          rowKey="id"
          size="small"
          columns={detailColumns}
          dataSource={detailData?.records ?? []}
          loading={detailLoading}
          pagination={{
            total: detailData?.total ?? 0,
            current: detailData?.pageNum ?? detailPageNum,
            pageSize: 10,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p) => setDetailPageNum(p),
          }}
        />
      </Drawer>
    </Card>
  )
}

// ==================== 计件价目 Tab ====================

/** 物料选择器（远程搜索，简单场景直接用 Select + options） */
function MaterialSelect({ value, onChange }: { value?: number; onChange?: (v?: number) => void }) {
  const [keyword, setKeyword] = useState('')
  const { data } = useQuery({
    queryKey: ['hr', 'materials', keyword],
    queryFn: () => supportApi.materials(keyword),
  })
  const options = (data ?? []).map((m: MaterialBrief) => ({
    label: `${m.code} / ${m.name}`,
    value: m.id,
  }))
  return (
    <Select
      showSearch
      filterOption={false}
      onSearch={setKeyword}
      placeholder="输入编码/名称搜索"
      allowClear
      options={options}
      value={value}
      onChange={onChange}
    />
  )
}

interface PriceFormValues {
  materialId: number
  processName: string
  unitPrice: number
  status: string
  remark?: string
}

function PricesTab() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string | undefined>()
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const [editing, setEditing] = useState<PiecePriceRow | null>(null)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<PriceFormValues>()

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['hr', 'piece-prices', keyword, status, pageNum, pageSize],
    queryFn: () =>
      piecePriceApi.page({ pageNum, pageSize, keyword: keyword || undefined, status }),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['hr', 'piece-prices'] })

  const saveMutation = useMutation({
    mutationFn: async (values: PriceFormValues) => {
      const payload = { ...values, remark: values.remark || undefined }
      if (editing) {
        await piecePriceApi.update(editing.id, payload)
      } else {
        await piecePriceApi.create(payload)
      }
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '新增成功')
      setOpen(false)
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: piecePriceApi.remove,
    onSuccess: () => {
      message.success('删除成功')
      invalidate()
    },
  })

  const columns: ColumnsType<PiecePriceRow> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 140, render: (v) => v ?? '-' },
    { title: '物料名称', dataIndex: 'materialName', width: 150, render: (v) => v ?? '-' },
    { title: '工序', dataIndex: 'processName', width: 110 },
    { title: '计件单价（元/件）', dataIndex: 'unitPrice', width: 140, align: 'right' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) =>
        v === 'ENABLED' ? <Tag color="success">启用</Tag> : <Tag color="default">停用</Tag>,
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v) => v ?? '-' },
    { title: '更新时间', dataIndex: 'updateTime', width: 170 },
    {
      title: '操作',
      key: 'action',
      width: 130,
      render: (_, record) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditing(record)
              form.setFieldsValue({
                materialId: record.materialId,
                processName: record.processName,
                unitPrice: record.unitPrice,
                status: record.status,
                remark: record.remark ?? undefined,
              })
              setOpen(true)
            }}
          >
            编辑
          </Button>
          <Popconfirm title="确认删除该价目？" onConfirm={() => deleteMutation.mutate(record.id)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <Card>
      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="物料编码/名称/工序 搜索"
          style={{ width: 240 }}
          allowClear
          onSearch={(v) => {
            setPageNum(1)
            setKeyword(v)
          }}
        />
        <Select
          placeholder="状态"
          style={{ width: 110 }}
          allowClear
          options={[
            { label: '启用', value: 'ENABLED' },
            { label: '停用', value: 'DISABLED' },
          ]}
          value={status}
          onChange={(v) => {
            setPageNum(1)
            setStatus(v)
          }}
        />
        <Button
          type="primary"
          onClick={() => {
            setEditing(null)
            form.resetFields()
            form.setFieldsValue({ status: 'ENABLED' })
            setOpen(true)
          }}
        >
          新增价目
        </Button>
      </Space>

      <Table<PiecePriceRow>
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

      <Modal
        title={editing ? `修改价目 - ${editing.processName}` : '新增计件价目'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        destroyOnClose
        width={520}
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Form.Item
            name="materialId"
            label="物料"
            rules={[{ required: true, message: '请选择物料' }]}
          >
            <MaterialSelect />
          </Form.Item>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="processName"
              label="工序名称"
              rules={[{ required: true, message: '请输入工序名称' }]}
              style={{ width: 220 }}
            >
              <Input placeholder="如：冲压成型" maxLength={50} />
            </Form.Item>
            <Form.Item
              name="unitPrice"
              label="计件单价（元/件）"
              rules={[{ required: true, message: '请输入单价' }]}
              style={{ width: 220 }}
            >
              <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="status" label="状态" initialValue="ENABLED" style={{ width: 220 }}>
              <Select
                options={[
                  { label: '启用', value: 'ENABLED' },
                  { label: '停用', value: 'DISABLED' },
                ]}
              />
            </Form.Item>
            <Form.Item name="remark" label="备注" style={{ width: 220 }}>
              <Input placeholder="备注" maxLength={200} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Card>
  )
}
