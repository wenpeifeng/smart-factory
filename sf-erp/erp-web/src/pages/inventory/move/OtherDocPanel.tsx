import HasPerm from '@/components/HasPerm'
import TableEllipsisText from '@/components/TableEllipsisText'
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import {
  AutoComplete,
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
  message,
} from 'antd'
import { PlusOutlined, MinusCircleOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  auditOtherIn,
  auditOtherOut,
  createOtherIn,
  createOtherOut,
  deleteOtherIn,
  deleteOtherOut,
  fetchMaterialOptions,
  fetchOtherInDetail,
  fetchOtherInPage,
  fetchOtherOutDetail,
  fetchOtherOutPage,
  fetchWarehouseOptions,
  fmtQty,
  type MaterialOption,
  type OtherDocDetail,
  type OtherDocRow,
} from '@/api/inventory'
import { formatDateTime } from '@/utils/date'

/** 面板类型：in=其他入库 out=其他出库 */
export type OtherDocKind = 'in' | 'out'

/** 单据表单值 */
interface OtherDocFormValues {
  warehouseId: number
  reason?: string
  remark?: string
  lines: { materialId: number; qty: number; remark?: string }[]
}

const REASON_SUGGESTIONS: Record<OtherDocKind, { value: string }[]> = {
  in: ['领用退回', '盘盈', '赠品', '样品', '其他'].map((v) => ({ value: v })),
  out: ['报损', '盘亏', '领用', '赠品', '样品', '其他'].map((v) => ({ value: v })),
}

const LIST_COLUMNS: ColumnsType<OtherDocRow> = [
  { title: '单号', dataIndex: 'no', width: 150 },
  { title: '仓库', dataIndex: 'warehouseName', width: 130, render: (v: string | null) => v ?? '-' },
  {
    title: '原因',
    dataIndex: 'reason',
    width: 220,
    render: (v: string | null) => <TableEllipsisText value={v} />,
  },
  { title: '行数', dataIndex: 'lineCount', width: 70, align: 'right' },
  {
    title: '总数量',
    dataIndex: 'totalQty',
    width: 110,
    align: 'right',
    render: (v: number) => fmtQty(v),
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 90,
    render: (v: number) =>
      v === 1 ? <Tag color="success">已审核</Tag> : <Tag color="default">草稿</Tag>,
  },
  { title: '审核时间', dataIndex: 'auditTime', width: 180, render: formatDateTime },
  { title: '创建时间', dataIndex: 'createTime', width: 180, render: formatDateTime },
]

/**
 * 其他出入库单面板：单据列表 + 新建（草稿）+ 审核过账 + 详情。
 * 入库/出库共用一套交互，通过 kind 切换接口与文案。
 */
export default function OtherDocPanel({ kind }: { kind: OtherDocKind }) {
  const isIn = kind === 'in'
  const docName = isIn ? '其他入库单' : '其他出库单'

  const api = isIn
    ? {
        page: fetchOtherInPage,
        detail: fetchOtherInDetail,
        create: createOtherIn,
        audit: auditOtherIn,
        remove: deleteOtherIn,
      }
    : {
        page: fetchOtherOutPage,
        detail: fetchOtherOutDetail,
        create: createOtherOut,
        audit: auditOtherOut,
        remove: deleteOtherOut,
      }

  const queryClient = useQueryClient()
  const [form] = Form.useForm<OtherDocFormValues>()
  const [filters, setFilters] = useState<{ status?: number; warehouseId?: number; keyword: string }>({
    keyword: '',
  })
  const [pagination, setPagination] = useState({ pageNum: 1, pageSize: 10 })
  const [createOpen, setCreateOpen] = useState(false)
  const [detailId, setDetailId] = useState<number | null>(null)

  // ---- 选项数据 ----
  const { data: warehouses } = useQuery({
    queryKey: ['inventory', 'warehouseOptions'],
    queryFn: fetchWarehouseOptions,
    staleTime: 5 * 60 * 1000,
  })
  const whOptions = (warehouses ?? []).map((w) => ({ label: `${w.code} ${w.name}`, value: w.id }))

  const { data: materialOptions } = useQuery({
    queryKey: ['inventory', 'materialOptions'],
    queryFn: () => fetchMaterialOptions(),
    staleTime: 5 * 60 * 1000,
  })
  const matOptions = (materialOptions ?? []).map((m: MaterialOption) => ({
    label: `${m.code} ${m.name}`,
    value: m.id,
  }))

  // ---- 列表 ----
  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['inventory', 'otherDoc', kind, filters, pagination],
    queryFn: () => api.page({ ...pagination, ...filters, keyword: filters.keyword || undefined }),
    placeholderData: keepPreviousData,
  })

  // ---- 详情 ----
  const { data: detail, isLoading: detailLoading } = useQuery({
    queryKey: ['inventory', 'otherDoc', kind, 'detail', detailId],
    queryFn: () => api.detail(detailId as number),
    enabled: detailId != null,
  })

  // ---- 变更 ----
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['inventory'] })

  const createMutation = useMutation({
    mutationFn: (values: OtherDocFormValues) => api.create(values),
    onSuccess: () => {
      message.success(`${docName}已创建（草稿）`)
      setCreateOpen(false)
      form.resetFields()
      invalidate()
    },
  })

  const auditMutation = useMutation({
    mutationFn: (id: number) => api.audit(id),
    onSuccess: () => {
      message.success('审核成功，库存已过账')
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.remove(id),
    onSuccess: () => {
      message.success('已删除')
      invalidate()
    },
  })

  const columns: ColumnsType<OtherDocRow> = [
    ...LIST_COLUMNS,
    {
      title: '操作',
      key: 'actions',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => setDetailId(record.id)}>
            详情
          </Button>
          {record.status === 0 && (
            <>
              <Popconfirm
                title="确认审核？"
                description="审核后库存将立即过账，不可撤销"
                onConfirm={() => auditMutation.mutate(record.id)}
              >
                <Button type="link" size="small">
                  审核
                </Button>
              </Popconfirm>
              <Popconfirm title="确认删除该草稿单据？" onConfirm={() => deleteMutation.mutate(record.id)}>
                <Button type="link" size="small" danger>
                  删除
                </Button>
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ]

  const detailLineColumns: ColumnsType<NonNullable<OtherDocDetail>['lines'][number]> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 120, render: (v: string | null) => v ?? '-' },
    { title: '物料名称', dataIndex: 'materialName', width: 220, render: (v: string | null) => v ?? '-' },
    { title: '数量', dataIndex: 'qty', width: 110, align: 'right', render: (v: number) => fmtQty(v) },
    {
      title: '备注',
      dataIndex: 'remark',
      width: 260,
      render: (v: string | null) => <TableEllipsisText value={v} />,
    },
  ]

  return (
    <Card size="small">
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          style={{ width: 120 }}
          placeholder="状态"
          allowClear
          options={[
            { label: '草稿', value: 0 },
            { label: '已审核', value: 1 },
          ]}
          value={filters.status}
          onChange={(v) => {
            setFilters((f) => ({ ...f, status: v }))
            setPagination((p) => ({ ...p, pageNum: 1 }))
          }}
        />
        <Select
          style={{ width: 200 }}
          placeholder="全部仓库"
          allowClear
          showSearch
          optionFilterProp="label"
          options={whOptions}
          value={filters.warehouseId}
          onChange={(v) => {
            setFilters((f) => ({ ...f, warehouseId: v }))
            setPagination((p) => ({ ...p, pageNum: 1 }))
          }}
        />
        <Input.Search
          style={{ width: 220 }}
          placeholder="单号搜索"
          allowClear
          onSearch={(v) => {
            setFilters((f) => ({ ...f, keyword: v }))
            setPagination((p) => ({ ...p, pageNum: 1 }))
          }}
        />
        <HasPerm code="inventory:move:add">
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            新建{docName}
          </Button>
        </HasPerm>
      </Space>

      <Table<OtherDocRow>
        className="erp-nowrap-table"
        rowKey="id"
        size="small"
        tableLayout="fixed"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading || isFetching}
        locale={{ emptyText: `暂无${docName}` }}
        scroll={{ x: 1370 }}
        pagination={{
          total: data?.total ?? 0,
          current: data?.pageNum ?? 1,
          pageSize: data?.pageSize ?? 10,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (pageNum, pageSize) => setPagination({ pageNum, pageSize }),
        }}
      />

      {/* 新建单据 */}
      <Modal
        title={`新建${docName}`}
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
        okText="保存草稿"
        width={720}
        destroyOnClose
      >
        <Form<OtherDocFormValues>
          form={form}
          layout="vertical"
          onFinish={(values) => createMutation.mutate(values)}
          initialValues={{ lines: [{}] }}
        >
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item
                name="warehouseId"
                label={isIn ? '入库仓库' : '出库仓库'}
                rules={[{ required: true, message: '请选择仓库' }]}
              >
                <Select placeholder="选择仓库" options={whOptions} showSearch optionFilterProp="label" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="reason" label="原因">
                <AutoComplete placeholder="选择或输入原因" options={REASON_SUGGESTIONS[kind]} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="remark" label="备注">
                <Input maxLength={200} />
              </Form.Item>
            </Col>
          </Row>

          <Form.List name="lines">
            {(fields, { add, remove }) => (
              <>
                {fields.map((field) => (
                  <Row key={field.key} gutter={12}>
                    <Col span={9}>
                      <Form.Item
                        name={[field.name, 'materialId']}
                        rules={[{ required: true, message: '请选择物料' }]}
                        style={{ marginBottom: 8 }}
                      >
                        <Select
                          placeholder="选择物料"
                          options={matOptions}
                          showSearch
                          optionFilterProp="label"
                          allowClear
                        />
                      </Form.Item>
                    </Col>
                    <Col span={5}>
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
                    <Col span={7}>
                      <Form.Item name={[field.name, 'remark']} style={{ marginBottom: 8 }}>
                        <Input placeholder="行备注（可选）" maxLength={200} />
                      </Form.Item>
                    </Col>
                    <Col span={3}>
                      {fields.length > 1 && (
                        <Button
                          type="text"
                          danger
                          icon={<MinusCircleOutlined />}
                          onClick={() => remove(field.name)}
                          style={{ marginTop: 4 }}
                        />
                      )}
                    </Col>
                  </Row>
                ))}
                <Button type="dashed" onClick={() => add()} icon={<PlusOutlined />} style={{ width: 240 }}>
                  添加单据行
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>

      {/* 单据详情 */}
      <Drawer
        title={`${docName}详情`}
        width={680}
        open={detailId != null}
        onClose={() => setDetailId(null)}
        loading={detailLoading}
      >
        {detail && (
          <>
            <Descriptions size="small" column={2} bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label="单号">{detail.no}</Descriptions.Item>
              <Descriptions.Item label="状态">
                {detail.status === 1 ? <Tag color="success">已审核</Tag> : <Tag>草稿</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label="原因">{detail.reason ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="审核时间">{formatDateTime(detail.auditTime)}</Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>
                {detail.remark ?? '-'}
              </Descriptions.Item>
            </Descriptions>
            <Table
              className="erp-nowrap-table"
              rowKey="id"
              size="small"
              tableLayout="fixed"
              columns={detailLineColumns}
              dataSource={detail.lines}
              pagination={false}
              locale={{ emptyText: '无明细行' }}
              scroll={{ x: 710 }}
            />
          </>
        )}
      </Drawer>
    </Card>
  )
}
