import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Form, Input, InputNumber, Modal, Select, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { ScanOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import HasPerm from '@/components/HasPerm'
import { getStepsByWorkorder, getWorkorders, type WoStep } from '@/api/plan'
import { getStationRecords, reportStation, type StationRecord } from '@/api/execution'
import { STEP_STATUS } from '@/api/plan'

/** 过站扫码报工页：选择工单工序 → 录入合格/报废/返修 → 过站（PASS 回传 ERP） */
export default function StationList() {
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [resultFilter, setResultFilter] = useState<string | undefined>()
  const [modalOpen, setModalOpen] = useState(false)
  const [woId, setWoId] = useState<number | null>(null)
  const [form] = Form.useForm()

  const { data, isLoading } = useQuery({
    queryKey: ['execution', 'stations', keyword, resultFilter],
    queryFn: () => getStationRecords({ pageNum: 1, pageSize: 20, keyword: keyword || undefined, result: resultFilter }),
  })
  const { data: producingWos } = useQuery({
    queryKey: ['plan', 'workorders', 'for-station'],
    queryFn: () => getWorkorders({ pageNum: 1, pageSize: 100, status: 'PRODUCING' }),
  })
  const enabled = woId != null
  const { data: steps } = useQuery({
    queryKey: ['plan', 'steps', woId],
    queryFn: () => getStepsByWorkorder(woId!),
    enabled,
  })

  const reportMutation = useMutation({
    mutationFn: (values: Record<string, unknown>) =>
      reportStation({
        woStepId: values.woStepId as number,
        sn: values.sn as string,
        goodQty: values.goodQty as number,
        scrapQty: values.scrapQty as number,
        reworkQty: (values.reworkQty as number) ?? 0,
        workMinutes: values.workMinutes as number | undefined,
        workerName: values.workerName as string,
        result: values.result as string,
      }),
    onSuccess: () => {
      message.success('过站报工成功')
      setModalOpen(false)
      form.resetFields()
      queryClient.invalidateQueries({ queryKey: ['execution', 'stations'] })
    },
  })

  const columns: ColumnsType<StationRecord> = [
    { title: '记录号', dataIndex: 'recordNo', width: 160 },
    { title: '工单号', dataIndex: 'woNo', width: 150 },
    { title: '工序', dataIndex: 'operationName', render: (_, r) => `${r.seqNo}-${r.operationName}` },
    { title: '合格', dataIndex: 'goodQty', width: 80, align: 'right' },
    { title: '报废', dataIndex: 'scrapQty', width: 80, align: 'right' },
    { title: '返修', dataIndex: 'reworkQty', width: 80, align: 'right' },
    { title: '报工人', dataIndex: 'workerName', width: 100 },
    { title: '过站时间', dataIndex: 'stationTime', width: 170 },
    {
      title: '结果',
      dataIndex: 'result',
      width: 90,
      render: (v: string) => <StatusTag value={v === 'PASS' ? '通过' : '不通过'} semantic={v === 'PASS' ? 'success' : 'error'} />,
    },
  ]

  return (
    <>
      <PageHeader
        title="过站报工"
        subtitle="车间终端扫码报工：过站即累计工序/工单产出，PASS 结果自动回传 ERP（幂等+失败重试）"
        extra={
          <HasPerm code="execution:station:report">
            <Button type="primary" icon={<ScanOutlined />} onClick={() => { setWoId(null); form.resetFields(); setModalOpen(true) }}>扫码报工</Button>
          </HasPerm>
        }
      />
      <Table<StationRecord>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        title={() => (
          <Space>
            <Input.Search placeholder="记录号/工单号/SN" allowClear style={{ width: 220 }} onSearch={setKeyword} />
            <Select
              allowClear
              placeholder="结果"
              style={{ width: 110 }}
              onChange={setResultFilter}
              options={[{ value: 'PASS', label: '通过' }, { value: 'FAIL', label: '不通过' }]}
            />
          </Space>
        )}
      />

      <Modal
        title="过站扫码报工"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={reportMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(v) => reportMutation.mutate(v)}>
          <Form.Item label="生产中工单" required>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择工单"
              value={woId ?? undefined}
              onChange={(v) => { setWoId(v); form.setFieldValue('woStepId', undefined) }}
              options={(producingWos?.records ?? []).map((w) => ({ value: w.id, label: `${w.woNo}（${w.materialName ?? ''}）` }))}
            />
          </Form.Item>
          <Form.Item name="woStepId" label="工序任务" rules={[{ required: true, message: '请选择工序' }]}>
            <Select
              placeholder="选择工序（需已派工）"
              disabled={!enabled}
              options={(steps ?? [])
                .filter((s: WoStep) => s.status === 'DISPATCHED' || s.status === 'PROCESSING')
                .map((s: WoStep) => ({ value: s.id, label: `${s.seqNo}-${s.operationName}（${STEP_STATUS[s.status]}）` }))}
            />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="goodQty" label="合格数量" initialValue={0} rules={[{ required: true }]}>
              <InputNumber min={0} precision={2} />
            </Form.Item>
            <Form.Item name="scrapQty" label="报废数量" initialValue={0}>
              <InputNumber min={0} precision={2} />
            </Form.Item>
            <Form.Item name="reworkQty" label="返修数量" initialValue={0}>
              <InputNumber min={0} precision={2} />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="sn" label="产品 SN">
              <Input placeholder="SN 模式工单必填" />
            </Form.Item>
            <Form.Item name="workMinutes" label="工时(分)">
              <InputNumber min={0} />
            </Form.Item>
            <Form.Item name="workerName" label="报工人">
              <Input placeholder="默认当前登录人" />
            </Form.Item>
            <Form.Item name="result" label="过站结果" initialValue="PASS">
              <Select style={{ width: 110 }} options={[{ value: 'PASS', label: '通过' }, { value: 'FAIL', label: '不通过' }]} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </>
  )
}
