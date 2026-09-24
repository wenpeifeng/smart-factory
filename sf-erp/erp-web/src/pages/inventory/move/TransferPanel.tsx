import HasPerm from '@/components/HasPerm'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { Button, Card, Col, Form, Input, InputNumber, Row, Select, Space, Table, Tag, message } from 'antd'
import { PlusOutlined, MinusCircleOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  fetchMaterialOptions,
  fetchMovementPage,
  fetchWarehouseOptions,
  fmtQty,
  submitTransfer,
  type MaterialOption,
  type MovementRow,
} from '@/api/inventory'

/** 调拨表单值 */
interface TransferFormValues {
  fromWarehouseId: number
  toWarehouseId: number
  remark?: string
  lines: { materialId: number; qty: number }[]
}

/** 最近调拨流水列 */
const MOVE_COLUMNS: ColumnsType<MovementRow> = [
  { title: '调拨单号', dataIndex: 'bizNo', width: 150, render: (v: string | null) => v ?? '-' },
  { title: '仓库', dataIndex: 'warehouseName', width: 120, render: (v: string | null) => v ?? '-' },
  { title: '物料', width: 200, render: (_, r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}` },
  {
    title: '方向/数量',
    dataIndex: 'qty',
    width: 120,
    align: 'right',
    render: (v: number) => (
      <span style={{ color: v >= 0 ? '#389e0d' : '#cf1322' }}>
        {v >= 0 ? '入 ' : '出 '}
        {fmtQty(Math.abs(v))}
      </span>
    ),
  },
  { title: '时间', dataIndex: 'createTime', width: 170 },
]

/**
 * 仓库调拨面板：表单即时执行（一出一入同事务），下方展示最近调拨流水。
 */
export default function TransferPanel() {
  const [form] = Form.useForm<TransferFormValues>()
  const queryClient = useQueryClient()

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

  // 最近调拨流水（MOVE）
  const { data: moves, isLoading } = useQuery({
    queryKey: ['inventory', 'movements', 'MOVE'],
    queryFn: () => fetchMovementPage({ pageNum: 1, pageSize: 10, bizType: 'MOVE' }),
    placeholderData: keepPreviousData,
  })

  const transferMutation = useMutation({
    mutationFn: submitTransfer,
    onSuccess: (data) => {
      message.success(`调拨成功，调拨单号 ${data.transferNo}`)
      form.resetFields()
      queryClient.invalidateQueries({ queryKey: ['inventory'] })
    },
  })

  const onFinish = (values: TransferFormValues) => {
    transferMutation.mutate(values)
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card title="仓库调拨（即时执行）" size="small">
        <Form<TransferFormValues>
          form={form}
          layout="vertical"
          onFinish={onFinish}
          initialValues={{ lines: [{}] }}
        >
          <Row gutter={12}>
            <Col span={6}>
              <Form.Item
                name="fromWarehouseId"
                label="调出仓库"
                rules={[{ required: true, message: '请选择调出仓库' }]}
              >
                <Select placeholder="调出仓库" options={whOptions} showSearch optionFilterProp="label" />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item
                name="toWarehouseId"
                label="调入仓库"
                dependencies={['fromWarehouseId']}
                rules={[
                  { required: true, message: '请选择调入仓库' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || value !== getFieldValue('fromWarehouseId')) {
                        return Promise.resolve()
                      }
                      return Promise.reject(new Error('调入仓库不能与调出仓库相同'))
                    },
                  }),
                ]}
              >
                <Select placeholder="调入仓库" options={whOptions} showSearch optionFilterProp="label" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="remark" label="备注">
                <Input placeholder="调拨原因/备注（可选）" maxLength={200} />
              </Form.Item>
            </Col>
          </Row>

          <Form.List name="lines">
            {(fields, { add, remove }) => (
              <>
                {fields.map((field) => (
                  <Row key={field.key} gutter={12}>
                    <Col span={10}>
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
                    <Col span={8}>
                      <Form.Item
                        name={[field.name, 'qty']}
                        rules={[{ required: true, message: '请输入数量' }]}
                        style={{ marginBottom: 8 }}
                      >
                        <InputNumber
                          placeholder="调拨数量"
                          min={0.0001}
                          precision={4}
                          style={{ width: '100%' }}
                        />
                      </Form.Item>
                    </Col>
                    <Col span={6}>
                      {fields.length > 1 && (
                        <Button
                          type="text"
                          danger
                          icon={<MinusCircleOutlined />}
                          onClick={() => remove(field.name)}
                          style={{ marginTop: 4 }}
                        >
                          删除行
                        </Button>
                      )}
                    </Col>
                  </Row>
                ))}
                <Button
                  type="dashed"
                  onClick={() => add()}
                  icon={<PlusOutlined />}
                  style={{ width: 240 }}
                >
                  添加调拨行
                </Button>
              </>
            )}
          </Form.List>

          <div style={{ marginTop: 16 }}>
            <HasPerm code="inventory:move:add">
              <Button type="primary" htmlType="submit" loading={transferMutation.isPending}>
                执行调拨
              </Button>
            </HasPerm>
            <Tag style={{ marginLeft: 12 }} color="blue">
              一出一入同事务，库存不足整单回滚
            </Tag>
          </div>
        </Form>
      </Card>

      <Card title="最近调拨流水" size="small">
        <Table<MovementRow>
          rowKey="id"
          size="small"
          columns={MOVE_COLUMNS}
          dataSource={moves?.records ?? []}
          loading={isLoading}
          pagination={false}
          locale={{ emptyText: '暂无调拨记录' }}
        />
      </Card>
    </Space>
  )
}
