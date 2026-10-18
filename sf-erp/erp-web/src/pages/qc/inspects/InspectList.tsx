import HasPerm from '@/components/HasPerm'
import TableEllipsisText from '@/components/TableEllipsisText'
import { useEffect, useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import {
  inspectApi,
  type QcInspectDetail,
  type QcInspectRow,
  type QcInspectSavePayload,
  type QcInspectSubmitPayload,
  type QcBizType,
  type InspectConclusion,
  type QcInspectPageParams,
} from '@/api/qc'
import {
  InspectTypeTag,
  InspectStatusTag,
  ConclusionTag,
  bizTypeText,
  INSPECT_TYPE_FILTER,
  BIZ_TYPE_OPTIONS,
  INSPECT_STATUS_OPTIONS,
  CONCLUSION_OPTIONS,
  fmtQty,
  useMaterialOptions,
  useSupplierOptions,
} from '../shared'

const { Title, Text } = Typography

/** 检验录入表头表单值 */
interface SubmitHeaderForm {
  inspectorName: string
  inspectDate: Dayjs
  qualifiedQty: number
  unqualifiedQty: number
  conclusion: InspectConclusion
  remark?: string
}

/** 明细行实检值编辑态（本地态承接用户输入，提交时组装） */
interface ItemEditState {
  id: number
  actualValue: string
  lineResult: 0 | 1 | null
}

/**
 * 检验单页：列表 + 从来源单生成 + 手工新增 + 检验录入 Drawer（实检值+结论）+ 详情 Drawer。
 * 草稿单走"检验录入"（提交后 草稿→已检验），已检验单走只读详情。
 */
export default function InspectList() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [params, setParams] = useState<QcInspectPageParams>({ pageNum: 1, pageSize: 10 })
  const [dateRange, setDateRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)

  // 检验录入 / 详情 Drawer
  const [target, setTarget] = useState<QcInspectDetail | null>(null)
  const [mode, setMode] = useState<'edit' | 'view'>('edit')
  const [submitForm] = Form.useForm<SubmitHeaderForm>()
  const [itemEdits, setItemEdits] = useState<ItemEditState[]>([])

  // 从来源单生成 / 手工新增弹窗
  const [genOpen, setGenOpen] = useState(false)
  const [genForm] = Form.useForm<{ bizType: QcBizType; bizId: number }>()
  const [createOpen, setCreateOpen] = useState(false)
  const [createForm] = Form.useForm<QcInspectSavePayload>()

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['qc', 'inspects', params],
    queryFn: () => inspectApi.page(params),
  })
  const materialOptions = useMaterialOptions()
  const supplierOptions = useSupplierOptions()

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['qc', 'inspects'] })

  /** 打开 Drawer 时按模式初始化（录入：表头默认值 + 明细空实检值） */
  useEffect(() => {
    if (target) {
      submitForm.resetFields()
      if (mode === 'edit') {
        submitForm.setFieldsValue({
          inspectorName: '',
          inspectDate: dayjs(),
          qualifiedQty: target.sampleQty,
          unqualifiedQty: 0,
          conclusion: 1,
        })
        setItemEdits(
          target.items.map((it) => ({ id: it.id, actualValue: '', lineResult: null })),
        )
      }
    }
  }, [target, mode, submitForm])

  const submitMutation = useMutation({
    mutationFn: (payload: QcInspectSubmitPayload) => inspectApi.submit(target!.id, payload),
    onSuccess: () => {
      message.success('检验提交成功')
      setTarget(null)
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: inspectApi.remove,
    onSuccess: () => {
      message.success('删除成功')
      invalidate()
    },
  })

  const genMutation = useMutation({
    mutationFn: ({ bizType, bizId }: { bizType: QcBizType; bizId: number }) =>
      inspectApi.generateFromBiz(bizType, bizId),
    onSuccess: (ids) => {
      message.success(ids.length ? `已生成 ${ids.length} 张检验单` : '该来源单的检验单均已存在，未重复生成')
      setGenOpen(false)
      invalidate()
    },
  })

  const createMutation = useMutation({
    mutationFn: (values: QcInspectSavePayload) => inspectApi.create(values),
    onSuccess: () => {
      message.success('检验单已创建（草稿）')
      setCreateOpen(false)
      invalidate()
    },
  })

  const openDetail = async (row: QcInspectRow, m: 'edit' | 'view') => {
    const detail = await inspectApi.get(row.id)
    setMode(m)
    setTarget(detail)
  }

  /** 提交检验（口径校验放后端，前端做轻提示） */
  const doSubmit = (values: SubmitHeaderForm) => {
    const payload: QcInspectSubmitPayload = {
      inspectorName: values.inspectorName,
      inspectDate: values.inspectDate.format('YYYY-MM-DD'),
      conclusion: values.conclusion,
      qualifiedQty: values.qualifiedQty,
      unqualifiedQty: values.unqualifiedQty,
      remark: values.remark,
      items: itemEdits.map((it) => ({
        id: it.id,
        actualValue: it.actualValue || undefined,
        lineResult: it.lineResult,
      })),
    }
    submitMutation.mutate(payload)
  }

  const columns: ColumnsType<QcInspectRow> = [
    { title: '检验单号', dataIndex: 'inspectNo', width: 150 },
    { title: '检验类型', dataIndex: 'inspectType', width: 100, render: (t) => <InspectTypeTag type={t} /> },
    {
      title: '来源',
      dataIndex: 'sourceType',
      width: 240,
      render: (v, record) => (
        <TableEllipsisText value={`${bizTypeText(v)}${record.sourceNo ? ` ${record.sourceNo}` : ''}`} />
      ),
    },
    {
      title: '物料',
      dataIndex: 'materialName',
      width: 180,
      ellipsis: true,
      render: (v, record) => (
        <>
          {record.materialCode} {v}
          {record.spec ? <Text type="secondary"> {record.spec}</Text> : null}
        </>
      ),
    },
    {
      title: '供应商',
      dataIndex: 'supplierName',
      width: 130,
      ellipsis: true,
      render: (v) => v ?? '-',
    },
    { title: '抽检数', dataIndex: 'sampleQty', width: 90, align: 'right', render: fmtQty },
    { title: '合格数', dataIndex: 'qualifiedQty', width: 90, align: 'right', render: fmtQty },
    { title: '不合格数', dataIndex: 'unqualifiedQty', width: 90, align: 'right', render: fmtQty },
    { title: '结论', dataIndex: 'conclusion', width: 95, render: (c) => <ConclusionTag conclusion={c} /> },
    { title: '状态', dataIndex: 'status', width: 85, render: (s) => <InspectStatusTag status={s} /> },
    { title: '检验人', dataIndex: 'inspectorName', width: 90, render: (v) => v ?? '-' },
    { title: '检验日期', dataIndex: 'inspectDate', width: 110, render: (v) => v ?? '-' },
    {
      title: '操作',
      key: 'action',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          {record.status === 0 ? (
            <Button type="link" size="small" onClick={() => openDetail(record, 'edit')}>
              检验录入
            </Button>
          ) : (
            <Button type="link" size="small" onClick={() => openDetail(record, 'view')}>
              详情
            </Button>
          )}
          {record.status === 0 ? (
            <HasPerm code="qc:inspect:delete">
            <Popconfirm title="确认删除该草稿检验单？" onConfirm={() => deleteMutation.mutate(record.id)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
            </HasPerm>
          ) : null}
        </Space>
      ),
    },
  ]

  /** 详情/录入明细列 */
  const itemColumns: ColumnsType<QcInspectDetail['items'][number]> = [
    { title: '#', dataIndex: 'seq', width: 46 },
    { title: '检验项目', dataIndex: 'itemName', width: 120 },
    { title: '标准值', dataIndex: 'standardValue', width: 110, render: (v) => v ?? '-' },
    {
      title: '标准范围',
      key: 'limit',
      width: 130,
      render: (_, it) =>
        it.lowerLimit == null && it.upperLimit == null
          ? '-'
          : `${it.lowerLimit ?? '—'} ~ ${it.upperLimit ?? '—'}`,
    },
    { title: '检验方法', dataIndex: 'inspectMethod', width: 100, render: (v) => v ?? '-' },
    { title: '抽检比例%', dataIndex: 'sampleRate', width: 90, align: 'right' },
    ...(mode === 'edit'
      ? [
          {
            title: '实检值',
            key: 'actualValue',
            width: 140,
            render: (_: unknown, it: QcInspectDetail['items'][number]) => (
              <Input
                value={itemEdits.find((e) => e.id === it.id)?.actualValue ?? ''}
                onChange={(e) =>
                  setItemEdits((list) =>
                    list.map((x) => (x.id === it.id ? { ...x, actualValue: e.target.value } : x)),
                  )
                }
                placeholder="实检值"
                maxLength={200}
              />
            ),
          } as ColumnsType<QcInspectDetail['items'][number]>[number],
          {
            title: '单项判定',
            key: 'lineResult',
            width: 110,
            render: (_: unknown, it: QcInspectDetail['items'][number]) => (
              <Select
                value={itemEdits.find((e) => e.id === it.id)?.lineResult ?? undefined}
                onChange={(v) =>
                  setItemEdits((list) =>
                    list.map((x) => (x.id === it.id ? { ...x, lineResult: v } : x)),
                  )
                }
                placeholder="判定"
                allowClear
                style={{ width: '100%' }}
                options={[
                  { value: 1, label: '合格' },
                  { value: 0, label: '不合格' },
                ]}
              />
            ),
          } as ColumnsType<QcInspectDetail['items'][number]>[number],
        ]
      : [
          {
            title: '实检值',
            dataIndex: 'actualValue',
            width: 140,
            render: (v: string | null) => v ?? '-',
          } as ColumnsType<QcInspectDetail['items'][number]>[number],
          {
            title: '单项判定',
            dataIndex: 'lineResult',
            width: 100,
            render: (v: 0 | 1 | null) =>
              v === 1 ? '合格' : v === 0 ? '不合格' : '-',
          } as ColumnsType<QcInspectDetail['items'][number]>[number],
        ]),
  ]

  /** 校验数量口径：合格+不合格=抽检数（前端即时反馈） */
  const qtyWatch = Form.useWatch([], submitForm)
  const qtyMismatch = useMemo(() => {
    if (!target || !qtyWatch) return false
    const sum = (qtyWatch.qualifiedQty ?? 0) + (qtyWatch.unqualifiedQty ?? 0)
    return sum !== target.sampleQty
  }, [qtyWatch, target])

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        检验单
      </Title>

      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <Input.Search
            placeholder="检验单号 / 来源单号模糊搜索"
            style={{ width: 240 }}
            allowClear
            onSearch={(v) => setParams((p) => ({ ...p, pageNum: 1, keyword: v || undefined }))}
          />
          <Select
            placeholder="检验类型"
            style={{ width: 130 }}
            allowClear
            options={INSPECT_TYPE_FILTER}
            onChange={(v) => setParams((p) => ({ ...p, pageNum: 1, inspectType: v }))}
          />
          <Select
            placeholder="来源类型"
            style={{ width: 130 }}
            allowClear
            options={BIZ_TYPE_OPTIONS}
            onChange={(v) => setParams((p) => ({ ...p, pageNum: 1, sourceType: v }))}
          />
          <Select
            placeholder="状态"
            style={{ width: 100 }}
            allowClear
            options={INSPECT_STATUS_OPTIONS}
            onChange={(v) => setParams((p) => ({ ...p, pageNum: 1, status: v }))}
          />
          <Select
            placeholder="结论"
            style={{ width: 120 }}
            allowClear
            options={CONCLUSION_OPTIONS}
            onChange={(v) => setParams((p) => ({ ...p, pageNum: 1, conclusion: v }))}
          />
          <DatePicker.RangePicker
            value={dateRange}
            onChange={(range) => {
              setDateRange(range)
              setParams((p) => ({
                ...p,
                pageNum: 1,
                dateBegin: range?.[0]?.format('YYYY-MM-DD'),
                dateEnd: range?.[1]?.format('YYYY-MM-DD'),
              }))
            }}
          />
          <HasPerm code="qc:inspect:add">
            <Button onClick={() => setGenOpen(true)}>从来源单生成</Button>
          </HasPerm>
          <HasPerm code="qc:inspect:add">
            <Button type="primary" onClick={() => {
              createForm.resetFields()
              createForm.setFieldsValue({ inspectType: 'IQC' })
              setCreateOpen(true)
            }}>
              手工新增
            </Button>
          </HasPerm>
        </Space>

        <Table<QcInspectRow>
          className="erp-nowrap-table"
          rowKey="id"
          tableLayout="fixed"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          scroll={{ x: 1600 }}
          pagination={{
            total: data?.total ?? 0,
            current: data?.pageNum ?? params.pageNum,
            pageSize: data?.pageSize ?? params.pageSize,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => setParams({ pageNum: p, pageSize: s }),
          }}
        />
      </Card>

      {/* 从来源单生成检验单 */}
      <Modal
        title="从来源单生成检验单"
        open={genOpen}
        onCancel={() => setGenOpen(false)}
        onOk={() => genForm.submit()}
        confirmLoading={genMutation.isPending}
        destroyOnClose
      >
        <Form form={genForm} layout="vertical" onFinish={(values) => genMutation.mutate(values)}>
          <Form.Item
            name="bizType"
            label="来源单据类型"
            rules={[{ required: true, message: '请选择来源单据类型' }]}
          >
            <Select options={BIZ_TYPE_OPTIONS} placeholder="选择类型" />
          </Form.Item>
          <Form.Item
            name="bizId"
            label="来源单据 ID"
            rules={[{ required: true, message: '请输入来源单据 ID' }]}
            extra="采购入库单/生产工单/销售退货单的主表 ID；按明细物料行各生成一张检验单，已生成的不重复"
          >
            <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="如 3" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 手工新增检验单 */}
      <Modal
        title="手工新增检验单（草稿）"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnClose
      >
        <Form form={createForm} layout="vertical" onFinish={(values) => createMutation.mutate(values)}>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="inspectType"
              label="检验类型"
              rules={[{ required: true, message: '请选择检验类型' }]}
              style={{ width: 200 }}
            >
              <Select options={INSPECT_TYPE_FILTER} />
            </Form.Item>
            <Form.Item
              name="sampleQty"
              label="抽检数量"
              rules={[{ required: true, message: '请输入抽检数量' }]}
              style={{ width: 200 }}
            >
              <InputNumber min={0.0001} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item
            name="materialId"
            label="物料"
            rules={[{ required: true, message: '请选择物料' }]}
            extra="检验项目将按 检验类型+物料分类 自动匹配启用模板带出"
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择物料"
              options={materialOptions.data ?? []}
            />
          </Form.Item>
          <Form.Item name="supplierId" label="供应商（IQC 可选）">
            <Select
              showSearch
              optionFilterProp="label"
              allowClear
              placeholder="选择供应商"
              options={supplierOptions.data ?? []}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 检验录入 / 详情 Drawer */}
      <Drawer
        title={
          mode === 'edit'
            ? `检验录入 - ${target?.inspectNo ?? ''}`
            : `检验单详情 - ${target?.inspectNo ?? ''}`
        }
        open={!!target}
        onClose={() => setTarget(null)}
        width={960}
        destroyOnClose
      >
        {target ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions size="small" bordered column={3}>
              <Descriptions.Item label="检验类型">
                <InspectTypeTag type={target.inspectType} />
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <InspectStatusTag status={target.status} />
              </Descriptions.Item>
              <Descriptions.Item label="结论">
                <ConclusionTag conclusion={target.conclusion} />
              </Descriptions.Item>
              <Descriptions.Item label="来源">
                {bizTypeText(target.sourceType)}
                {target.sourceNo ? ` ${target.sourceNo}` : ''}
              </Descriptions.Item>
              <Descriptions.Item label="物料">
                {target.materialCode} {target.materialName}
                {target.spec ? `（${target.spec}）` : ''}
              </Descriptions.Item>
              <Descriptions.Item label="供应商">{target.supplierName ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="抽检数">{fmtQty(target.sampleQty)}</Descriptions.Item>
              <Descriptions.Item label="套用模板">{target.templateName ?? '（未匹配模板）'}</Descriptions.Item>
              <Descriptions.Item label="检验人">{target.inspectorName ?? '-'}</Descriptions.Item>
            </Descriptions>

            {mode === 'edit' ? (
              <Form form={submitForm} layout="vertical" onFinish={doSubmit}>
                <Space size={16} wrap>
                  <Form.Item
                    name="inspectorName"
                    label="检验人"
                    rules={[{ required: true, message: '请输入检验人' }]}
                    style={{ width: 160 }}
                  >
                    <Input placeholder="检验人姓名" maxLength={50} />
                  </Form.Item>
                  <Form.Item
                    name="inspectDate"
                    label="检验日期"
                    rules={[{ required: true, message: '请选择检验日期' }]}
                  >
                    <DatePicker allowClear={false} />
                  </Form.Item>
                  <Form.Item
                    name="qualifiedQty"
                    label="合格数"
                    rules={[{ required: true, message: '必填' }]}
                  >
                    <InputNumber min={0} style={{ width: 120 }} />
                  </Form.Item>
                  <Form.Item
                    name="unqualifiedQty"
                    label="不合格数"
                    rules={[{ required: true, message: '必填' }]}
                  >
                    <InputNumber min={0} style={{ width: 120 }} />
                  </Form.Item>
                  <Form.Item
                    name="conclusion"
                    label="检验结论"
                    rules={[{ required: true, message: '请选择结论' }]}
                  >
                    <Select options={CONCLUSION_OPTIONS} style={{ width: 140 }} />
                  </Form.Item>
                </Space>
                {qtyMismatch ? (
                  <Typography.Text type="danger">
                    合格数 + 不合格数 必须等于抽检数（{fmtQty(target.sampleQty)}）
                  </Typography.Text>
                ) : null}
                <Form.Item name="remark" label="备注">
                  <Input.TextArea rows={2} maxLength={500} placeholder="备注" />
                </Form.Item>

                <Typography.Title level={5}>检验项目（录入实检值）</Typography.Title>
                <Table
                  rowKey="id"
                  size="small"
                  columns={itemColumns}
                  dataSource={target.items}
                  pagination={false}
                />
                <Space style={{ marginTop: 16 }}>
                  <Button
                    type="primary"
                    htmlType="submit"
                    loading={submitMutation.isPending}
                    disabled={qtyMismatch || target.items.length === 0}
                  >
                    提交检验
                  </Button>
                  <Text type="secondary">提交后检验单转为已检验，不可再修改</Text>
                </Space>
              </Form>
            ) : (
              <>
                <Table
                  rowKey="id"
                  size="small"
                  columns={itemColumns}
                  dataSource={target.items}
                  pagination={false}
                />
                {target.remark ? (
                  <Descriptions size="small" bordered column={1}>
                    <Descriptions.Item label="备注">{target.remark}</Descriptions.Item>
                  </Descriptions>
                ) : null}
              </>
            )}
          </Space>
        ) : null}
      </Drawer>
    </div>
  )
}
