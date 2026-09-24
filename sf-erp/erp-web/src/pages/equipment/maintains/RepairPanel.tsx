import { useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Table,
  Tag,
} from 'antd'
import { PlusOutlined, MinusCircleOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  REPAIR_STATUS_COLOR,
  REPAIR_STATUS_OPTIONS,
  acceptRepair,
  closeRepair,
  completeRepair,
  fetchDeviceOptions,
  fetchMaterialOptions,
  fetchRepairDetail,
  fetchRepairPage,
  fetchWarehouseOptions,
  fmtMoney,
  fmtQty,
  type RepairDetail,
  type RepairRow,
} from '@/api/equipment'

/** 接单表单值 */
interface AcceptFormValues {
  repairer: string
}

/** 完工表单值（备件行可动态增删） */
interface CompleteFormValues {
  faultReason?: string
  warehouseId?: number
  lines: { materialId: number; qty: number }[]
}

/**
 * 维修工单面板：工单列表 + 接单 + 完工（含备件行编辑，提交即出库）+ 关闭 + 详情。
 * 完工接口在后端同事务扣库存，任一备件不足时整体回滚并提示。
 */
export default function RepairPanel() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [filters, setFilters] = useState<{ status?: number; deviceId?: number; keyword: string }>({
    keyword: '',
  })
  const [pagination, setPagination] = useState({ pageNum: 1, pageSize: 10 })

  const [acceptTarget, setAcceptTarget] = useState<RepairRow | null>(null)
  const [acceptForm] = Form.useForm<AcceptFormValues>()
  const [completeTarget, setCompleteTarget] = useState<RepairRow | null>(null)
  const [completeForm] = Form.useForm<CompleteFormValues>()
  const [detailId, setDetailId] = useState<number | null>(null)

  // ---- 选项数据 ----
  const { data: deviceOptions } = useQuery({
    queryKey: ['equipment', 'deviceOptions'],
    queryFn: fetchDeviceOptions,
    staleTime: 5 * 60 * 1000,
  })
  const devOptions = (deviceOptions ?? []).map((d) => ({
    label: `${d.code} ${d.name}`,
    value: d.id,
  }))

  const { data: warehouseOptions } = useQuery({
    queryKey: ['equipment', 'warehouseOptions'],
    queryFn: fetchWarehouseOptions,
    staleTime: 5 * 60 * 1000,
  })
  const whOptions = (warehouseOptions ?? []).map((w) => ({
    label: `${w.code} ${w.name}`,
    value: w.id,
  }))

  const { data: materialOptions } = useQuery({
    queryKey: ['equipment', 'materialOptions'],
    queryFn: () => fetchMaterialOptions(),
    staleTime: 5 * 60 * 1000,
  })
  const matOptions = (materialOptions ?? []).map((m) => ({
    label: `${m.code} ${m.name}`,
    value: m.id,
  }))

  // ---- 列表 / 详情 ----
  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['equipment', 'repairs', filters, pagination],
    queryFn: () =>
      fetchRepairPage({
        ...pagination,
        status: filters.status,
        deviceId: filters.deviceId,
        keyword: filters.keyword || undefined,
      }),
    placeholderData: keepPreviousData,
  })

  const { data: detail, isLoading: detailLoading } = useQuery({
    queryKey: ['equipment', 'repairs', 'detail', detailId],
    queryFn: () => fetchRepairDetail(detailId as number),
    enabled: detailId != null,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['equipment'] })

  const acceptMutation = useMutation({
    mutationFn: ({ id, repairer }: { id: number; repairer: string }) => acceptRepair(id, repairer),
    onSuccess: () => {
      message.success('已接单，工单进入维修中')
      setAcceptTarget(null)
      acceptForm.resetFields()
      invalidate()
    },
  })

  const completeMutation = useMutation({
    mutationFn: ({ id, values }: { id: number; values: CompleteFormValues }) =>
      completeRepair(id, {
        faultReason: values.faultReason,
        warehouseId: values.warehouseId,
        lines: (values.lines ?? []).filter((l) => l.materialId && l.qty),
      }),
    onSuccess: () => {
      message.success('完工成功，备件已出库')
      setCompleteTarget(null)
      completeForm.resetFields()
      invalidate()
    },
  })

  const closeMutation = useMutation({
    mutationFn: closeRepair,
    onSuccess: () => {
      message.success('工单已关闭')
      invalidate()
    },
  })

  const columns: ColumnsType<RepairRow> = [
    { title: '工单号', dataIndex: 'repairNo', width: 145 },
    { title: '设备', dataIndex: 'deviceName', width: 150, ellipsis: true, render: (v, r) => v ?? r.deviceCode ?? '-' },
    {
      title: '故障描述',
      dataIndex: 'faultDesc',
      ellipsis: true,
      render: (v, r) => v ?? r.deviceCode,
    },
    { title: '报修人', dataIndex: 'reporter', width: 85, render: (v) => v ?? '-' },
    { title: '报修时间', dataIndex: 'reportTime', width: 165 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 85,
      render: (_, record) => (
        <Tag color={REPAIR_STATUS_COLOR[record.status]}>{record.statusLabel}</Tag>
      ),
    },
    { title: '维修人', dataIndex: 'repairer', width: 85, render: (v) => v ?? '-' },
    { title: '完成时间', dataIndex: 'finishTime', width: 165, render: (v) => v ?? '-' },
    {
      title: '备件成本',
      dataIndex: 'spareCost',
      width: 100,
      align: 'right',
      render: (v: number) => fmtMoney(v),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => setDetailId(record.id)}>
            详情
          </Button>
          {record.status === 0 && (
            <Button type="link" size="small" onClick={() => setAcceptTarget(record)}>
              接单
            </Button>
          )}
          {record.status === 1 && (
            <Button type="link" size="small" onClick={() => openComplete(record)}>
              完工
            </Button>
          )}
          {record.status === 2 && (
            <Popconfirm title="确认关闭该工单？" onConfirm={() => closeMutation.mutate(record.id)}>
              <Button type="link" size="small">
                关闭
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  const openComplete = (record: RepairRow) => {
    setCompleteTarget(record)
    completeForm.resetFields()
    completeForm.setFieldsValue({ lines: [{}] })
  }

  const detailLineColumns: ColumnsType<NonNullable<RepairDetail>['lines'][number]> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 130, render: (v) => v ?? '-' },
    { title: '物料名称', dataIndex: 'materialName', render: (v) => v ?? '-' },
    { title: '数量', dataIndex: 'qty', width: 110, align: 'right', render: (v: number) => fmtQty(v) },
    {
      title: '成本参考',
      dataIndex: 'costRef',
      width: 110,
      align: 'right',
      render: (v: number) => fmtMoney(v),
    },
  ]

  return (
    <Card size="small">
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          style={{ width: 120 }}
          placeholder="全部状态"
          allowClear
          options={REPAIR_STATUS_OPTIONS}
          value={filters.status}
          onChange={(v) => {
            setFilters((f) => ({ ...f, status: v }))
            setPagination((p) => ({ ...p, pageNum: 1 }))
          }}
        />
        <Select
          style={{ width: 200 }}
          placeholder="全部设备"
          allowClear
          showSearch
          optionFilterProp="label"
          options={devOptions}
          value={filters.deviceId}
          onChange={(v) => {
            setFilters((f) => ({ ...f, deviceId: v }))
            setPagination((p) => ({ ...p, pageNum: 1 }))
          }}
        />
        <Input.Search
          style={{ width: 220 }}
          placeholder="工单号 / 设备搜索"
          allowClear
          onSearch={(v) => {
            setFilters((f) => ({ ...f, keyword: v }))
            setPagination((p) => ({ ...p, pageNum: 1 }))
          }}
        />
      </Space>

      <Table<RepairRow>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading || isFetching}
        locale={{ emptyText: '暂无维修工单' }}
        scroll={{ x: 1320 }}
        pagination={{
          total: data?.total ?? 0,
          current: data?.pageNum ?? 1,
          pageSize: data?.pageSize ?? 10,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (pageNum, pageSize) => setPagination({ pageNum, pageSize }),
        }}
      />

      {/* 接单弹窗 */}
      <Modal
        title={`接单 - ${acceptTarget?.repairNo ?? ''}`}
        open={!!acceptTarget}
        onCancel={() => setAcceptTarget(null)}
        onOk={() => acceptForm.submit()}
        confirmLoading={acceptMutation.isPending}
        okText="确认接单"
        destroyOnClose
      >
        <Form form={acceptForm} layout="vertical" onFinish={(values) => acceptTarget && acceptMutation.mutate({ id: acceptTarget.id, repairer: values.repairer })}>
          <Form.Item
            name="repairer"
            label="维修人"
            rules={[{ required: true, message: '请输入维修人' }]}
          >
            <Input placeholder="维修人姓名" maxLength={50} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 完工弹窗（备件行编辑） */}
      <Modal
        title={`完工 - ${completeTarget?.repairNo ?? ''}`}
        open={!!completeTarget}
        onCancel={() => setCompleteTarget(null)}
        onOk={() => completeForm.submit()}
        confirmLoading={completeMutation.isPending}
        okText="确认完工"
        width={720}
        destroyOnClose
      >
        <Form
          form={completeForm}
          layout="vertical"
          onFinish={(values) =>
            completeTarget && completeMutation.mutate({ id: completeTarget.id, values })
          }
        >
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="faultReason" label="故障原因">
                <Input.TextArea placeholder="故障原因（选填）" maxLength={500} rows={2} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="warehouseId"
                label="备件出库仓库"
                rules={[{ required: true, message: '使用备件时必须选择仓库' }]}
                extra="不使用备件可清空；出库不足将整体回滚"
              >
                <Select placeholder="选择仓库" options={whOptions} showSearch optionFilterProp="label" allowClear />
              </Form.Item>
            </Col>
          </Row>

          <Form.List name="lines">
            {(fields, { add, remove }) => (
              <>
                {fields.map((field) => (
                  <Row key={field.key} gutter={12}>
                    <Col span={12}>
                      <Form.Item
                        name={[field.name, 'materialId']}
                        rules={[{ required: true, message: '请选择备件' }]}
                        style={{ marginBottom: 8 }}
                      >
                        <Select
                          placeholder="选择备件物料"
                          options={matOptions}
                          showSearch
                          optionFilterProp="label"
                          allowClear
                        />
                      </Form.Item>
                    </Col>
                    <Col span={8}>
                      <Form.Item
                        name={[field.name, 'qty']}
                        rules={[{ required: true, message: '请输入数量' }]}
                        style={{ marginBottom: 8 }}
                      >
                        <InputNumber
                          placeholder="数量"
                          min={0.0001}
                          precision={4}
                          style={{ width: '100%' }}
                        />
                      </Form.Item>
                    </Col>
                    <Col span={4}>
                      <Button
                        type="text"
                        danger
                        icon={<MinusCircleOutlined />}
                        onClick={() => remove(field.name)}
                        style={{ marginTop: 4 }}
                      />
                    </Col>
                  </Row>
                ))}
                <Button type="dashed" onClick={() => add()} icon={<PlusOutlined />} style={{ width: 240 }}>
                  添加备件行
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>

      {/* 工单详情 */}
      <Drawer
        title={`维修工单详情 - ${detail?.repairNo ?? ''}`}
        width={680}
        open={detailId != null}
        onClose={() => setDetailId(null)}
        loading={detailLoading}
      >
        {detail && (
          <>
            <Descriptions size="small" column={2} bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label="设备">
                {detail.deviceCode ?? '-'} {detail.deviceName ?? ''}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={REPAIR_STATUS_COLOR[detail.status]}>{detail.statusLabel ?? detail.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="报修人">{detail.reporter ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="报修时间">{detail.reportTime ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="维修人">{detail.repairer ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="完成时间">{detail.finishTime ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="故障描述" span={2}>
                {detail.faultDesc ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="故障原因" span={2}>
                {detail.faultReason ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>
                {detail.remark ?? '-'}
              </Descriptions.Item>
            </Descriptions>
            <Table
              rowKey="id"
              size="small"
              columns={detailLineColumns}
              dataSource={detail.lines}
              pagination={false}
              locale={{ emptyText: '无备件消耗' }}
            />
          </>
        )}
      </Drawer>
    </Card>
  )
}
