import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { Dayjs } from 'dayjs'
import PageHeader from '@/components/PageHeader'
import StatusTag, { type StatusSemantic } from '@/components/StatusTag'
import { useSubmitting } from '@/hooks/useSubmitting'
import {
  capacityApi,
  dispatchApi,
  workCenterApi,
  workReportApi,
  type CapacityLoadRow,
  type WoStepRow,
  type WorkReportRow,
} from '@/api/mrp'

const { Text } = Typography

/** 工序任务状态 -> 中文 + 语义 */
const STEP_STATUS: Record<string, { label: string; semantic: StatusSemantic }> = {
  PENDING_DISPATCH: { label: '待派工', semantic: 'default' },
  DISPATCHED: { label: '已派工', semantic: 'processing' },
  PROCESSING: { label: '加工中', semantic: 'processing' },
  FINISHED: { label: '已完工', semantic: 'success' },
}

const renderStepStatus = (v: string) => {
  const s = STEP_STATUS[v]
  return s ? <StatusTag value={s.label} semantic={s.semantic} /> : v
}

const STEP_STATUS_OPTIONS = Object.entries(STEP_STATUS).map(([value, s]) => ({
  value,
  label: s.label,
}))

/** 分钟数展示（<60 显示分钟，>=60 显示 x小时y分） */
function fmtMinutes(min: number): string {
  if (min < 60) return `${min}分钟`
  const h = Math.floor(min / 60)
  const m = min % 60
  return m === 0 ? `${h}小时` : `${h}小时${m}分`
}

/** 工序任务卡片：看板的最小单元 */
function StepCard({
  step,
  onDispatch,
  onReport,
}: {
  step: WoStepRow
  onDispatch: (step: WoStepRow) => void
  onReport: (step: WoStepRow) => void
}) {
  const progress = Math.min(100, Math.max(0, step.progress ?? 0))
  return (
    <Card
      size="small"
      style={{ height: '100%' }}
      title={
        <span>
          <Text strong>#{step.seqNo}</Text> {step.operationName ?? step.operationCode ?? '工序'}
        </span>
      }
      extra={renderStepStatus(step.status)}
    >
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        <div>
          <Text type="secondary">订单</Text> {step.orderNo ?? '-'}
        </div>
        <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          <Text type="secondary">成品</Text> {step.materialCode ?? '-'} {step.materialName ?? ''}
        </div>
        <div>
          <Text type="secondary">计划</Text> {step.planQty} 件 × {step.unitMinutes} 分钟/件
        </div>
        <div>
          <Text type="secondary">时间</Text> {step.planStartTime ?? '-'} ~ {step.planEndTime ?? '-'}
        </div>
        {step.assignee && (
          <div>
            <Text type="secondary">派给</Text> {step.assignee}
          </div>
        )}
        <div>
          <Progress
            percent={progress}
            size="small"
            status={step.status === 'FINISHED' ? 'success' : progress > 0 ? 'active' : 'normal'}
            format={() => `${step.finishQty}/${step.planQty}`}
          />
        </div>
        {(step.badQty > 0 || step.actualMinutes > 0) && (
          <div>
            {step.badQty > 0 && (
              <Text type="danger">不合格 {step.badQty} </Text>
            )}
            {step.actualMinutes > 0 && <Text type="secondary">实际 {fmtMinutes(step.actualMinutes)}</Text>}
          </div>
        )}
        <Space size={4} style={{ marginTop: 4 }}>
          {step.status === 'PENDING_DISPATCH' && (
            <Button type="primary" size="small" onClick={() => onDispatch(step)}>
              派工
            </Button>
          )}
          {step.status !== 'PENDING_DISPATCH' && step.status !== 'FINISHED' && (
            <Button type="primary" size="small" ghost onClick={() => onReport(step)}>
              报工
            </Button>
          )}
          {step.status === 'FINISHED' && (
            <Text type="success">累计合格 {step.finishQty}，已完工</Text>
          )}
        </Space>
      </Space>
    </Card>
  )
}

/** 任务看板 Tab：按工作中心分组的卡片墙 + 派工/报工 Modal */
function BoardTab() {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<string | undefined>()
  const [workCenterId, setWorkCenterId] = useState<number | undefined>()

  // 派工/报工弹窗目标
  const [dispatchTarget, setDispatchTarget] = useState<WoStepRow | null>(null)
  const [reportTarget, setReportTarget] = useState<WoStepRow | null>(null)

  const { data: workCenters } = useQuery({
    queryKey: ['mrp', 'work-centers', 'all'],
    queryFn: workCenterApi.listAll,
  })

  const { data: board, isLoading } = useQuery({
    queryKey: ['mrp', 'dispatch-board', status, workCenterId],
    queryFn: () =>
      dispatchApi.board({ status, workCenterId }),
  })

  // 报工明细（当前报工目标任务的最近记录）
  const { data: reportHistory } = useQuery({
    queryKey: ['mrp', 'work-reports', reportTarget?.id],
    queryFn: () => workReportApi.list({ woStepId: reportTarget!.id }),
    enabled: reportTarget != null,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['mrp', 'dispatch-board'] })

  const [dispatchForm] = Form.useForm<{ assignee: string; planStartTime?: Dayjs }>()
  const dispatchSubmit = useSubmitting(
    async (values: { assignee: string; planStartTime?: Dayjs }) => {
      await dispatchApi.dispatch(dispatchTarget!.id, {
        assignee: values.assignee.trim(),
        planStartTime: values.planStartTime
          ? values.planStartTime.format('YYYY-MM-DD HH:mm:ss')
          : undefined,
      })
    },
    {
      successText: '派工成功（已派工）',
      onSuccess: () => {
        setDispatchTarget(null)
        invalidate()
      },
    },
  )

  const [reportForm] = Form.useForm<{
    employee: string
    goodQty: number
    badQty: number
    workMinutes?: number
    qcNote?: string
  }>()
  const reportSubmit = useSubmitting(
    async (values: { employee: string; goodQty: number; badQty: number; workMinutes?: number; qcNote?: string }) => {
      await workReportApi.report({
        woStepId: reportTarget!.id,
        employee: values.employee.trim(),
        goodQty: values.goodQty,
        badQty: values.badQty,
        workMinutes: values.workMinutes,
        qcNote: values.qcNote || undefined,
      })
    },
    {
      successText: '报工成功',
      onSuccess: () => {
        setReportTarget(null)
        invalidate()
        void queryClient.invalidateQueries({ queryKey: ['mrp', 'work-reports'] })
      },
    },
  )

  const historyColumns: ColumnsType<WorkReportRow> = [
    { title: '员工', dataIndex: 'employee', width: 90 },
    { title: '合格数', dataIndex: 'goodQty', width: 80, align: 'right' },
    { title: '不合格', dataIndex: 'badQty', width: 76, align: 'right' },
    { title: '工时(分)', dataIndex: 'workMinutes', width: 80, align: 'right' },
    { title: '质量说明', dataIndex: 'qcNote', ellipsis: true, render: (v) => v ?? '-' },
    { title: '报工时间', dataIndex: 'reportTime', width: 165 },
  ]

  return (
    <>
      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <Select
            placeholder="任务状态"
            allowClear
            style={{ width: 130 }}
            value={status}
            onChange={(v) => setStatus(v)}
            options={STEP_STATUS_OPTIONS}
          />
          <Select
            placeholder="工作中心"
            allowClear
            showSearch
            optionFilterProp="label"
            style={{ width: 200 }}
            value={workCenterId}
            onChange={(v) => setWorkCenterId(v)}
            options={(workCenters ?? []).map((w) => ({
              value: w.id,
              label: `${w.code} | ${w.name}`,
            }))}
          />
        </Space>

        {isLoading ? (
          <div style={{ textAlign: 'center', padding: 48 }}>
            <Spin />
          </div>
        ) : (board?.groups ?? []).length === 0 ? (
          <Empty description="暂无工序任务（生产订单下达且成品有已审核工艺路线时自动生成）" />
        ) : (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            {(board?.groups ?? []).map((group) => (
              <Card
                key={group.workCenterId ?? 'ungrouped'}
                size="small"
                title={
                  <span>
                    {group.workCenterName ?? '未分组'}
                    {group.capacityHoursPerDay != null && (
                      <Text type="secondary" style={{ fontWeight: 400, marginLeft: 8, fontSize: 13 }}>
                        产能 {group.capacityHoursPerDay} 小时/天
                      </Text>
                    )}
                  </span>
                }
                extra={<Text type="secondary">{group.stepCount} 个任务</Text>}
              >
                <Row gutter={[12, 12]}>
                  {group.steps.map((step) => (
                    <Col key={step.id} xs={24} sm={12} lg={8} xl={6}>
                      <StepCard
                        step={step}
                        onDispatch={setDispatchTarget}
                        onReport={setReportTarget}
                      />
                    </Col>
                  ))}
                </Row>
              </Card>
            ))}
          </Space>
        )}
      </Card>

      {/* 派工 Modal */}
      <Modal
        title={`派工 - ${dispatchTarget?.operationName ?? ''}（订单 ${dispatchTarget?.orderNo ?? ''}）`}
        open={dispatchTarget != null}
        onCancel={() => setDispatchTarget(null)}
        onOk={() => dispatchForm.submit()}
        confirmLoading={dispatchSubmit.loading}
        destroyOnClose
      >
        <Form
          form={dispatchForm}
          layout="vertical"
          onFinish={(values) => dispatchSubmit.run(values)}
        >
          <Form.Item
            name="assignee"
            label="派工对象（员工姓名或设备）"
            rules={[{ required: true, message: '请输入派工对象' }]}
          >
            <Input placeholder="如 张师傅" maxLength={50} />
          </Form.Item>
          <Form.Item name="planStartTime" label="计划开工时间（不填保留顺排时间）">
            <DatePicker
              showTime
              style={{ width: 220 }}
              placeholder={dispatchTarget?.planStartTime ?? '默认顺排时间'}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 报工 Modal（含本任务报工历史） */}
      <Modal
        title={`报工 - ${reportTarget?.operationName ?? ''}（订单 ${reportTarget?.orderNo ?? ''}）`}
        open={reportTarget != null}
        onCancel={() => setReportTarget(null)}
        onOk={() => reportForm.submit()}
        confirmLoading={reportSubmit.loading}
        width={680}
        destroyOnClose
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <div>
            计划 <Text strong>{reportTarget?.planQty}</Text> 件，累计合格{' '}
            <Text strong>{reportTarget?.finishQty}</Text>，剩余{' '}
            <Text type="warning">
              {reportTarget ? reportTarget.planQty - reportTarget.finishQty : 0}
            </Text>{' '}
            件；累计合格达到计划数量即工序完工。
          </div>
          <Form
            form={reportForm}
            layout="vertical"
            onFinish={(values) => reportSubmit.run(values)}
            initialValues={{ badQty: 0 }}
          >
            <Space size={16} wrap>
              <Form.Item
                name="employee"
                label="报工员工"
                rules={[{ required: true, message: '请输入报工员工姓名' }]}
              >
                <Input placeholder="如 李工人" maxLength={50} style={{ width: 140 }} />
              </Form.Item>
              <Form.Item
                name="goodQty"
                label="合格数"
                rules={[{ required: true, message: '请输入合格数' }]}
              >
                <InputNumber min={0} precision={0} style={{ width: 120 }} />
              </Form.Item>
              <Form.Item name="badQty" label="不合格数" initialValue={0}>
                <InputNumber min={0} precision={0} style={{ width: 110 }} />
              </Form.Item>
              <Form.Item name="workMinutes" label="实际工时(分钟)">
                <InputNumber min={0} precision={0} style={{ width: 130 }} />
              </Form.Item>
            </Space>
            <Form.Item
              noStyle
              shouldUpdate={(prev, cur) => (prev.badQty ?? 0) !== (cur.badQty ?? 0)}
            >
              {({ getFieldValue }) =>
                (getFieldValue('badQty') ?? 0) > 0 ? (
                  <Form.Item
                    name="qcNote"
                    label="质量待处理说明（不合格时填写，供质检跟进）"
                    tooltip="不填将自动生成默认待处理说明"
                  >
                    <Input.TextArea rows={2} maxLength={500} placeholder="如 表面划伤，待返工处理" />
                  </Form.Item>
                ) : null
              }
            </Form.Item>
          </Form>

          <div style={{ fontWeight: 600 }}>本任务报工记录</div>
          <Table<WorkReportRow>
            rowKey="id"
            size="small"
            columns={historyColumns}
            dataSource={reportHistory ?? []}
            pagination={false}
            locale={{ emptyText: '暂无报工记录' }}
          />
        </Space>
      </Modal>
    </>
  )
}

/** 能力负荷 Tab：期间各工作中心 负荷工时/可用工时/负荷率 + 超载清单 */
function CapacityTab() {
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null)
  const [workCenterId, setWorkCenterId] = useState<number | undefined>()

  const { data: workCenters } = useQuery({
    queryKey: ['mrp', 'work-centers', 'all'],
    queryFn: workCenterApi.listAll,
  })

  const params = {
    from: range?.[0]?.format('YYYY-MM-DD'),
    to: range?.[1]?.format('YYYY-MM-DD'),
    workCenterId,
  }
  const { data, isLoading } = useQuery({
    queryKey: ['mrp', 'capacity-load', params],
    queryFn: () => capacityApi.load(params),
  })

  const columns: ColumnsType<CapacityLoadRow> = [
    { title: '编码', dataIndex: 'code', width: 140 },
    { title: '工作中心', dataIndex: 'name', width: 130 },
    { title: '车间', dataIndex: 'workshop', width: 110, render: (v) => v ?? '-' },
    {
      title: '负荷工时(h)',
      dataIndex: 'loadHours',
      width: 110,
      align: 'right',
      render: (v, row) => <Text strong={row.overloaded}>{v}</Text>,
    },
    { title: '可用工时(h)', dataIndex: 'availableHours', width: 110, align: 'right' },
    {
      title: '负荷率',
      dataIndex: 'loadRate',
      width: 200,
      render: (v: number | null, row) =>
        v == null ? (
          row.loadHours > 0 ? (
            <Text type="danger">产能为0，无法承载</Text>
          ) : (
            '-'
          )
        ) : (
          <Progress
            percent={Math.min(v, 100)}
            size="small"
            strokeColor={v > 100 ? '#ff4d4f' : undefined}
            format={() => `${v}%`}
            status={v > 100 ? 'exception' : v > 80 ? 'active' : 'normal'}
          />
        ),
    },
    { title: '未完工任务', dataIndex: 'pendingStepCount', width: 100, align: 'right' },
    {
      title: '状态',
      key: 'state',
      width: 90,
      render: (_, row) =>
        row.overloaded ? (
          <StatusTag value="超载" semantic="error" />
        ) : (
          <StatusTag value="正常" semantic="success" />
        ),
    },
  ]

  return (
    <Card>
      <Space style={{ marginBottom: 16 }} wrap>
        <DatePicker.RangePicker
          value={range}
          onChange={(v) => setRange(v as [Dayjs, Dayjs] | null)}
          placeholder={['开始日期', '结束日期']}
          allowEmpty={[false, false]}
        />
        <Select
          placeholder="工作中心"
          allowClear
          showSearch
          optionFilterProp="label"
          style={{ width: 200 }}
          value={workCenterId}
          onChange={(v) => setWorkCenterId(v)}
          options={(workCenters ?? []).map((w) => ({
            value: w.id,
            label: `${w.code} | ${w.name}`,
          }))}
        />
      </Space>

      {(data?.overloads ?? []).length > 0 && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message={`超载工作中心 ${data!.overloads.length} 个：${data!.overloads
            .map((o) => `${o.name}（${o.loadRate ?? '-'}%）`)
            .join('、')}`}
          description="负荷按未完工工序任务的 计划数量×单件工时 汇总；可用工时 = 每日产能 × 期间工作日数（周末不计）。建议外协分流或顺延计划。"
        />
      )}

      <div style={{ marginBottom: 8 }}>
        {data ? (
          <Text type="secondary">
            统计期间 {data.fromDate} ~ {data.toDate}，工作日 {data.workdays} 天
          </Text>
        ) : (
          <Text type="secondary">默认统计今天起 7 天</Text>
        )}
      </div>

      <Table<CapacityLoadRow>
        rowKey="workCenterId"
        size="small"
        columns={columns}
        dataSource={data?.rows ?? []}
        loading={isLoading}
        pagination={false}
        rowClassName={(row) => (row.overloaded ? 'ant-table-row-danger' : '')}
        locale={{ emptyText: '暂无工作中心，请先在工艺路线页维护' }}
      />
    </Card>
  )
}

/**
 * 派工报工页（二期）：工序任务看板（按工作中心分组卡片 + 派工/报工 + 进度条）
 * + 能力负荷 Tab（CRP 简版：负荷/可用/负荷率与超载清单）。
 */
export default function DispatchPage() {
  return (
    <div className="erp-page">
      <PageHeader
        title="派工报工"
        subtitle="生产订单下达自动生成的工序任务；派工到人、报工回写进度，全部工序完工即整单工序完成"
      />
      <Tabs
        defaultActiveKey="board"
        items={[
          { key: 'board', label: '任务看板', children: <BoardTab /> },
          { key: 'capacity', label: '能力负荷', children: <CapacityTab /> },
        ]}
      />
    </div>
  )
}
