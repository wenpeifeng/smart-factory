import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Button, Card, Input, InputNumber, List, Modal, Space, Tag, Typography, message } from 'antd'
import { LogoutOutlined, ScanOutlined, SendOutlined } from '@ant-design/icons'
import { MobileShell } from '@/mobile/shared'
import { useAuthStore } from '@/stores/auth'
import { getStepsByWorkorder, getWorkorders, STEP_STATUS, type WoStep } from '@/api/plan'
import { reportStation } from '@/api/execution'

/**
 * 移动端扫码报工作业页（/m/scan?type=report）。
 * 流程：输入/扫工单号 → 选择已派工工序 → 录合格/报废/返修 → 提交过站（PASS 回传 ERP）。
 */
export default function MobileScan() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { user, logout } = useAuthStore()
  const [code, setCode] = useState('')
  const [woId] = useState<number | null>(null)
  const [reportOpen, setReportOpen] = useState(false)
  const [step, setStep] = useState<WoStep | null>(null)
  const [goodQty, setGoodQty] = useState(1)
  const [scrapQty, setScrapQty] = useState(0)
  const [reworkQty, setReworkQty] = useState(0)
  const type = params.get('type') ?? 'report'
  const typeLabel = { report: '过站报工', check: '首检', andon: '安灯' }[type] ?? '过站报工'

  // 生产中工单（取最近 50）
  const { data: wos, refetch: refetchWos, isFetching } = useQuery({
    queryKey: ['m', 'workorders'],
    queryFn: () => getWorkorders({ pageNum: 1, pageSize: 50 }),
  })
  const candidates = (wos?.records ?? []).filter(
    (w) => w.status === 'RELEASED' || w.status === 'PRODUCING',
  )
  const matched = code.trim()
    ? candidates.find((w) => w.woNo.includes(code.trim())) ?? null
    : null

  const { data: steps } = useQuery({
    queryKey: ['m', 'steps', matched?.id ?? woId],
    queryFn: () => getStepsByWorkorder((matched?.id ?? woId)!),
    enabled: matched != null || woId != null,
  })
  const reportable = (steps ?? []).filter(
    (s) => s.status === 'DISPATCHED' || s.status === 'PROCESSING',
  )

  const reportMutation = useMutation({
    mutationFn: (stepId: number) =>
      reportStation({ woStepId: stepId, goodQty, scrapQty, reworkQty, workerName: user?.nickname ?? user?.username ?? '', result: 'PASS' }),
    onSuccess: () => {
      message.success('过站报工成功')
      setReportOpen(false)
      setStep(null)
      setGoodQty(1)
      setScrapQty(0)
      setReworkQty(0)
      queryClient.invalidateQueries({ queryKey: ['m', 'steps'] })
    },
  })

  return (
    <MobileShell
      title={`MES · ${typeLabel}`}
      extra={
        <Button
          type="text"
          icon={<LogoutOutlined />}
          style={{ color: '#fff' }}
          onClick={() => {
            logout()
            navigate('/m/login', { replace: true })
          }}
        />
      }
    >
      <Card size="small">
        <Input.Search
          size="large"
          prefix={<ScanOutlined />}
          placeholder="扫描/输入工单号"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          onSearch={() => refetchWos()}
          loading={isFetching}
          enterButton
        />
      </Card>

      {matched && (
        <Card size="small" title={`工单 ${matched.woNo}`} style={{ marginTop: 12 }}>
          <Typography.Text type="secondary">
            {matched.materialName ?? ''} · 计划 {matched.qty} · 已完工 {matched.finishQty}
          </Typography.Text>
        </Card>
      )}

      {matched && (
        <Card size="small" title="可报工工序（已派工）" style={{ marginTop: 12 }}>
          {reportable.length === 0 ? (
            <Typography.Text type="secondary">暂无已派工工序（请先在计划模块派工）</Typography.Text>
          ) : (
            <List
              size="small"
              dataSource={reportable}
              renderItem={(s) => (
                <List.Item
                  actions={[
                    <Button key="r" type="link" size="small" icon={<SendOutlined />} onClick={() => { setStep(s); setReportOpen(true) }}>
                      报工
                    </Button>,
                  ]}
                >
                  {s.seqNo} - {s.operationName}
                  <Tag style={{ marginLeft: 8 }}>{STEP_STATUS[s.status] ?? s.status}</Tag>
                </List.Item>
              )}
            />
          )}
        </Card>
      )}

      {!matched && code.trim() === '' && (
        <Card size="small" title="生产中工单" style={{ marginTop: 12 }}>
          <Space direction="vertical" style={{ width: '100%' }}>
            {candidates.slice(0, 10).map((w) => (
              <Button key={w.id} block onClick={() => setCode(w.woNo)}>
                {w.woNo} · {w.materialName ?? ''}
              </Button>
            ))}
            {candidates.length === 0 && <Typography.Text type="secondary">暂无生产中工单</Typography.Text>}
          </Space>
        </Card>
      )}

      <Modal
        title={`过站报工：${step ? `${step.seqNo}-${step.operationName}` : ''}`}
        open={reportOpen}
        onCancel={() => setReportOpen(false)}
        onOk={() => step && reportMutation.mutate(step.id)}
        confirmLoading={reportMutation.isPending}
        destroyOnClose
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Space size="middle" style={{ display: 'flex' }}>
            <div>
              <Typography.Text type="secondary">合格数量</Typography.Text>
              <InputNumber min={0} value={goodQty} onChange={(v) => setGoodQty(v ?? 0)} style={{ width: 110 }} />
            </div>
            <div>
              <Typography.Text type="secondary">报废数量</Typography.Text>
              <InputNumber min={0} value={scrapQty} onChange={(v) => setScrapQty(v ?? 0)} style={{ width: 110 }} />
            </div>
            <div>
              <Typography.Text type="secondary">返修数量</Typography.Text>
              <InputNumber min={0} value={reworkQty} onChange={(v) => setReworkQty(v ?? 0)} style={{ width: 110 }} />
            </div>
          </Space>
          <Typography.Text type="secondary">报工人：{user?.nickname ?? user?.username ?? ''}；提交后累计工单/工序产出并回传 ERP。</Typography.Text>
        </Space>
      </Modal>
    </MobileShell>
  )
}
