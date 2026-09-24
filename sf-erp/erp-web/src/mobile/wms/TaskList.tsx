import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Input,
  InputNumber,
  List,
  Modal,
  Segmented,
  Space,
  Tag,
  Typography,
} from 'antd'
import { LogoutOutlined } from '@ant-design/icons'
import { MobileShell } from '@/mobile/shared'
import { useAuthStore } from '@/stores/auth'
import {
  TASK_STATUS_MAP,
  TASK_TYPE_MAP,
  claimWmsTask,
  completeWmsTask,
  fetchWmsTaskPage,
  fmtQty,
  type WmsTaskRow,
} from '@/api/inventory'

const { Text } = Typography

/** 移动端状态页签：待分配(0) / 待执行+执行中(10,20) / 我的历史(30) */
type TabKey = 'pending' | 'doing' | 'done'

/**
 * 移动端 WMS 任务页（/m/wms/tasks）：任务领取 → 开始 → 完成（支持实际数量/库位确认）。
 * 扫码能力 = 手工输入/摄像头扫码确认任务号或库位编码（核心为列表点选操作）。
 */
export default function TaskList() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const logout = useAuthStore((s) => s.logout)
  const user = useAuthStore((s) => s.user)

  const tab = (['pending', 'doing', 'done'].includes(params.get('tab') ?? '')
    ? params.get('tab')
    : 'pending') as TabKey

  const [completeTarget, setCompleteTarget] = useState<WmsTaskRow | null>(null)
  const [actualQty, setActualQty] = useState<number | null>(null)
  const [taskNoInput, setTaskNoInput] = useState('')

  const statusFilter = tab === 'pending' ? 0 : tab === 'doing' ? 10 : 30
  const { data, isFetching } = useQuery({
    queryKey: ['inventory', 'mobileWmsTasks', statusFilter],
    queryFn: () => fetchWmsTaskPage({ status: statusFilter, pageSize: 50 }),
    refetchInterval: 15_000,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['inventory'] })

  const claimAndStart = useMutation({
    mutationFn: (id: number) => claimWmsTask(id),
    onSuccess: () => {
      invalidate()
    },
  })

  const completeMutation = useMutation({
    mutationFn: () =>
      completeWmsTask(completeTarget?.id as number, {
        actualQty: actualQty ?? undefined,
      }),
    onSuccess: () => {
      Modal.success({ title: '任务完成', content: '库存事务已提交' })
      setCompleteTarget(null)
      setActualQty(null)
      invalidate()
    },
  })

  // 按任务号快速定位（扫码枪输入回车）
  const matched = taskNoInput.trim()
    ? (data?.records ?? []).find((t) => t.taskNo === taskNoInput.trim())
    : null

  const renderItem = (t: WmsTaskRow) => (
    <List.Item
      style={{ display: 'block' }}
      actions={
        tab === 'pending'
          ? [
              <Button
                key="claim"
                type="primary"
                size="small"
                loading={claimAndStart.isPending}
                onClick={() => claimAndStart.mutate(t.id)}
              >
                领取
              </Button>,
            ]
          : tab === 'doing'
            ? [
                <Button
                  key="done"
                  type="primary"
                  danger
                  size="small"
                  onClick={() => {
                    setCompleteTarget(t)
                    setActualQty(t.qty)
                  }}
                >
                  完成
                </Button>,
              ]
            : undefined
      }
    >
      <List.Item.Meta
        title={
          <Space size={6} wrap>
            <Tag color="blue">{TASK_TYPE_MAP[t.taskType] ?? t.taskType}</Tag>
            <span className="font-mono text-sm">{t.taskNo}</span>
            <Tag style={{ marginInlineEnd: 0 }}>{TASK_STATUS_MAP[t.status] ?? t.status}</Tag>
          </Space>
        }
        description={
          <Space direction="vertical" size={2} style={{ width: '100%' }}>
            <span className="text-sm">
              {t.materialCode ?? t.materialId} {t.materialName ?? ''}
              {t.batchNo ? <Tag style={{ marginInlineStart: 6 }}>{t.batchNo}</Tag> : null}
            </span>
            <span className="text-xs text-gray-500">
              数量 <Text strong>{fmtQty(t.qty)}</Text> · 库位{' '}
              <Text strong>{t.locationCode ?? '未定位'}</Text>
              {t.fromLocationCode ? ` · ${t.fromLocationCode} → ${t.locationCode ?? '-'}` : ''}
            </span>
            {t.sourceNo ? <span className="text-xs text-gray-400">来源 {t.sourceNo}</span> : null}
          </Space>
        }
      />
    </List.Item>
  )

  return (
    <MobileShell
      title="WMS 作业任务"
      extra={
        <Space size={4}>
          <span className="text-xs opacity-80">{user?.nickname ?? user?.username}</span>
          <Button
            type="text"
            size="small"
            icon={<LogoutOutlined style={{ color: '#fff' }} />}
            onClick={() => {
              logout()
              navigate('/m/login', { replace: true })
            }}
          />
        </Space>
      }
    >
      <Segmented
        block
        value={tab}
        onChange={(v) => navigate(`/m/wms/tasks?tab=${v}`, { replace: true })}
        options={[
          { label: '待领取', value: 'pending' },
          { label: '执行中', value: 'doing' },
          { label: '已完成', value: 'done' },
        ]}
        style={{ marginBottom: 12 }}
      />

      <Card size="small" style={{ marginBottom: 12 }}>
        <Input.Search
          size="large"
          value={taskNoInput}
          onChange={(e) => setTaskNoInput(e.target.value)}
          placeholder="扫任务号条码 / 输入任务号定位"
          allowClear
        />
        {matched && (
          <Alert
            className="mt-2"
            type="success"
            showIcon
            message={
              <Space size={6} wrap>
                <span>
                  {matched.taskNo} · {TASK_TYPE_MAP[matched.taskType]} · {fmtQty(matched.qty)} ·{' '}
                  {matched.locationCode ?? '未定位'}
                </span>
                {matched.status === 0 && (
                  <Button size="small" type="primary" onClick={() => claimAndStart.mutate(matched.id)}>
                    领取
                  </Button>
                )}
                {(matched.status === 10 || matched.status === 20) && (
                  <Button
                    size="small"
                    danger
                    type="primary"
                    onClick={() => {
                      setCompleteTarget(matched)
                      setActualQty(matched.qty)
                    }}
                  >
                    完成
                  </Button>
                )}
              </Space>
            }
          />
        )}
      </Card>

      <Card size="small">
        <List
          size="small"
          loading={isFetching}
          dataSource={data?.records ?? []}
          locale={{ emptyText: '暂无任务（扫描器每 30 秒自动生成）' }}
          renderItem={renderItem}
        />
      </Card>

      {/* 完成确认：实际数量（部分完成） */}
      <Modal
        title={completeTarget ? `完成任务 ${completeTarget.taskNo}` : '完成任务'}
        open={completeTarget != null}
        onCancel={() => {
          setCompleteTarget(null)
          setActualQty(null)
        }}
        onOk={() => completeMutation.mutate()}
        confirmLoading={completeMutation.isPending}
        okText="确认完成"
        okButtonProps={{ danger: true }}
        destroyOnClose
      >
        {completeTarget && (
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            <Descriptions size="small" column={1} bordered>
              <Descriptions.Item label="类型">{TASK_TYPE_MAP[completeTarget.taskType]}</Descriptions.Item>
              <Descriptions.Item label="物料">
                {completeTarget.materialCode ?? completeTarget.materialId} {completeTarget.materialName ?? ''}
              </Descriptions.Item>
              <Descriptions.Item label="库位">{completeTarget.locationCode ?? '未定位'}</Descriptions.Item>
              <Descriptions.Item label="任务数量">{fmtQty(completeTarget.qty)}</Descriptions.Item>
            </Descriptions>
            <InputNumber
              size="large"
              style={{ width: '100%' }}
              min={0.0001}
              precision={4}
              value={actualQty ?? undefined}
              onChange={(v) => setActualQty(v)}
              addonBefore="实际数量"
            />
            <p className="text-xs text-gray-500">
              上架=库存从未上架行转移到库位行（总量守恒）；来源拣选=账面已在出库审核时扣减。
            </p>
          </Space>
        )}
      </Modal>
    </MobileShell>
  )
}
