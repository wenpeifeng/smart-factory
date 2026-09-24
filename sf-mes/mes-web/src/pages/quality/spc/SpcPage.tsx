import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, Form, InputNumber, Modal, Popconfirm, Select, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ReferenceLine, ResponsiveContainer } from 'recharts'
import { PlusOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import HasPerm from '@/components/HasPerm'
import { deleteSpcRule, getSpcChart, getSpcRules, saveSpcRule, type QcSpcRule } from '@/api/quality'
import { getMaterials } from '@/api/basedata'

/** SPC 控制图页：规则维护 + 控制图（UCL/LCL/中心线 + 判异标记） */
export default function SpcPage() {
  const queryClient = useQueryClient()
  const [form] = Form.useForm()
  const [modalOpen, setModalOpen] = useState(false)
  const [chartRuleId, setChartRuleId] = useState<number | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['quality', 'spc', 'rules'],
    queryFn: () => getSpcRules({ pageNum: 1, pageSize: 50 }),
  })
  const { data: materials } = useQuery({ queryKey: ['basedata', 'materials', 'for-spc'], queryFn: () => getMaterials({ pageNum: 1, pageSize: 200 }) })
  const { data: chart } = useQuery({
    queryKey: ['quality', 'spc', 'chart', chartRuleId],
    queryFn: () => getSpcChart(chartRuleId!, 50),
    enabled: chartRuleId != null,
  })

  const saveMutation = useMutation({
    mutationFn: (values: Record<string, unknown>) =>
      saveSpcRule({
        materialId: values.materialId as number,
        itemName: values.itemName as string,
        ucl: values.ucl as number,
        lcl: values.lcl as number,
        target: (values.target as number) ?? undefined,
      }),
    onSuccess: () => {
      message.success('规则已保存')
      setModalOpen(false)
      queryClient.invalidateQueries({ queryKey: ['quality', 'spc', 'rules'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteSpcRule(id),
    onSuccess: () => {
      message.success('已删除')
      queryClient.invalidateQueries({ queryKey: ['quality', 'spc', 'rules'] })
    },
  })

  const columns: ColumnsType<QcSpcRule> = [
    { title: '物料', render: (_, r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}` },
    { title: '检验项目', dataIndex: 'itemName' },
    { title: 'LCL', dataIndex: 'lcl', width: 80, align: 'right' },
    { title: 'UCL', dataIndex: 'ucl', width: 80, align: 'right' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: number) => <StatusTag value={v === 1 ? '启用' : '停用'} semantic={v === 1 ? 'success' : 'default'} />,
    },
    {
      title: '操作',
      width: 160,
      render: (_, r) => (
        <Space size={4}>
          <Button size="small" type="link" onClick={() => setChartRuleId(r.id)}>控制图</Button>
          <HasPerm code="quality:spc:edit">
            <Popconfirm title="确认删除该规则及样本？" onConfirm={() => deleteMutation.mutate(r.id)}>
              <Button size="small" type="link" danger>删除</Button>
            </Popconfirm>
          </HasPerm>
        </Space>
      ),
    },
  ]

  const chartData = (chart?.samples ?? []).map((s, i) => ({ idx: i + 1, value: s.value, violations: s.violations }))

  return (
    <>
      <PageHeader
        title="SPC 控制图"
        subtitle="检验提交自动采样；判异准则 R1 超限 / R2 连续9点同侧 / R3 连续6点趋势 / R5 3点中2点同侧B区外"
        extra={
          <HasPerm code="quality:spc:edit">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => { form.resetFields(); setModalOpen(true) }}>新建规则</Button>
          </HasPerm>
        }
      />
      <Table<QcSpcRule>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 50, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
      />

      {chart && (
        <Card title={`控制图：${chart.rule.materialCode ?? ''} / ${chart.rule.itemName}`} style={{ marginTop: 16 }}>
          <ResponsiveContainer width="100%" height={280}>
            <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 8, left: 8 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="idx" />
              <YAxis domain={[Number(chart.rule.lcl) - 1, Number(chart.rule.ucl) + 1]} />
              <Tooltip />
              <ReferenceLine y={chart.rule.ucl} stroke="red" strokeDasharray="6 3" label="UCL" />
              <ReferenceLine y={chart.rule.lcl} stroke="red" strokeDasharray="6 3" label="LCL" />
              <ReferenceLine y={chart.center} stroke="#1668dc" strokeDasharray="3 3" label="CL" />
              <Line type="monotone" dataKey="value" stroke="#1668dc" dot={{ r: 3 }} isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
        </Card>
      )}

      <Modal title="新建 SPC 规则" open={modalOpen} onCancel={() => setModalOpen(false)} onOk={() => form.submit()} confirmLoading={saveMutation.isPending} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={(v) => saveMutation.mutate(v)}>
          <Form.Item name="materialId" label="物料" rules={[{ required: true, message: '请选择物料' }]}>
            <Select showSearch optionFilterProp="label" options={(materials?.records ?? []).map((m) => ({ value: m.id, label: `${m.code} ${m.name}` }))} />
          </Form.Item>
          <Form.Item name="itemName" label="检验项目名（与检验模板项目一致）" rules={[{ required: true }]}>
            <Select
              showSearch
              options={["长度", "宽度", "重量", "外观", "电压", "其他"].map((v) => ({ value: v, label: v }))}
            />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="lcl" label="控制下限 LCL" rules={[{ required: true }]}>
              <InputNumber style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="target" label="中心线（空=中点）">
              <InputNumber style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="ucl" label="控制上限 UCL" rules={[{ required: true }]}>
              <InputNumber style={{ width: 140 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </>
  )
}
