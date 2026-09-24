import { useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  Checkbox,
  Empty,
  List,
  Progress,
  Space,
  Tag,
  Typography,
  message,
} from 'antd'
import { ArrowLeftOutlined, LogoutOutlined } from '@ant-design/icons'
import { MobileShell } from '@/mobile/shared'
import { useAuthStore } from '@/stores/auth'
import {
  TASK_STATUS_MAP,
  claimWmsTask,
  completeWmsTask,
  completeWmsWave,
  fetchWavePickPath,
  fetchWmsWaveDetail,
  fetchWmsWavePage,
  fmtQty,
} from '@/api/inventory'

const { Text } = Typography

const WAVE_STATUS_MAP: Record<number, string> = {
  0: '建波',
  10: '拣选中',
  20: '已完成',
}

/**
 * 移动端按波次拣选页（/m/wms/waves 与 /m/wms/waves/:id）：
 * 波次列表 → 波次详情（按拣选路径序=库位编码升序）→ 逐条勾选完成 → 完成波次。
 */
export default function PickByWave() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const logout = useAuthStore((s) => s.logout)
  const user = useAuthStore((s) => s.user)
  const waveId = id ? Number(id) : null

  // 本地勾选态（完成即勾选，刷新以服务端状态为准）
  const [checked, setChecked] = useState<Record<number, boolean>>({})

  const { data: waves, isFetching: wavesLoading } = useQuery({
    queryKey: ['inventory', 'mobileWaves'],
    queryFn: () => fetchWmsWavePage({ pageSize: 50 }),
    enabled: waveId == null,
    refetchInterval: 20_000,
  })

  const { data: wave } = useQuery({
    queryKey: ['inventory', 'mobileWaveDetail', waveId],
    queryFn: () => fetchWmsWaveDetail(waveId as number),
    enabled: waveId != null,
  })
  const { data: path, isFetching: pathLoading } = useQuery({
    queryKey: ['inventory', 'mobileWavePath', waveId],
    queryFn: () => fetchWavePickPath(waveId as number),
    enabled: waveId != null,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['inventory'] })

  const claimMutation = useMutation({
    mutationFn: (taskId: number) => claimWmsTask(taskId),
    onSuccess: () => invalidate(),
  })
  const completeMutation = useMutation({
    mutationFn: (taskId: number) => completeWmsTask(taskId),
    onSuccess: (_d, taskId) => {
      setChecked((c) => ({ ...c, [taskId]: true }))
      message.success('拣选完成')
      invalidate()
    },
  })
  const completeWaveMutation = useMutation({
    mutationFn: () => completeWmsWave(waveId as number),
    onSuccess: () => {
      message.success('波次已完成')
      setChecked({})
      navigate('/m/wms/waves', { replace: true })
      invalidate()
    },
  })

  const shellExtra = (
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
  )

  // ==================== 列表态 ====================
  if (waveId == null) {
    return (
      <MobileShell title="波次拣选" extra={shellExtra}>
        <Card size="small">
          <List
            size="small"
            loading={wavesLoading}
            dataSource={waves?.records ?? []}
            locale={{ emptyText: <Empty description="暂无波次" /> }}
            renderItem={(w) => (
              <List.Item
                onClick={() => navigate(`/m/wms/waves/${w.id}`)}
                style={{ cursor: 'pointer' }}
                extra={
                  <Tag color={w.status === 20 ? 'success' : w.status === 10 ? 'processing' : 'default'}>
                    {WAVE_STATUS_MAP[w.status] ?? w.status}
                  </Tag>
                }
              >
                <List.Item.Meta
                  title={<span className="font-mono">{w.waveNo}</span>}
                  description={
                    <Space size={8}>
                      <span>{w.warehouseName ?? '-'}</span>
                      <span>
                        {w.doneCount}/{w.taskCount} 任务
                      </span>
                      <span>{fmtQty(w.totalQty)}</span>
                    </Space>
                  }
                />
              </List.Item>
            )}
          />
        </Card>
        <p className="mt-3 text-xs text-gray-500">点波次进入按库位编码排序的拣选路径，逐条完成。</p>
        <Button block size="large" style={{ marginTop: 12 }} onClick={() => setSearchParams({})}>
          刷新
        </Button>
      </MobileShell>
    )
  }

  // ==================== 详情态（拣选路径） ====================
  const tasks = path ?? []
  const doneCount = tasks.filter((t) => t.status === 30 || checked[t.id]).length
  const allDone = tasks.length > 0 && doneCount === tasks.length

  return (
    <MobileShell
      title={wave ? `波次 ${wave.waveNo}` : '波次拣选'}
      extra={
        <Button
          type="text"
          size="small"
          icon={<ArrowLeftOutlined style={{ color: '#fff' }} />}
          onClick={() => navigate('/m/wms/waves', { replace: true })}
        />
      }
    >
      <Card size="small" style={{ marginBottom: 12 }}>
        <Progress percent={tasks.length ? Math.round((doneCount / tasks.length) * 100) : 0} size="small" />
        <Space size={12} wrap style={{ marginTop: 4 }}>
          <span className="text-xs">
            {doneCount} / {tasks.length} 任务
          </span>
          <span className="text-xs">总数量 {fmtQty(wave?.totalQty ?? 0)}</span>
          {wave ? <Tag>{WAVE_STATUS_MAP[wave.status] ?? wave.status}</Tag> : null}
        </Space>
      </Card>

      <Card size="small">
        <List
          size="small"
          loading={pathLoading}
          dataSource={tasks}
          locale={{ emptyText: '波内无任务' }}
          renderItem={(t, index) => {
            const done = t.status === 30 || checked[t.id]
            return (
              <List.Item
                style={{ opacity: done ? 0.55 : 1 }}
                extra={
                  done ? (
                    <Checkbox checked disabled />
                  ) : t.status === 0 ? (
                    <Button size="small" loading={claimMutation.isPending} onClick={() => claimMutation.mutate(t.id)}>
                      领取
                    </Button>
                  ) : (
                    <Button
                      size="small"
                      type="primary"
                      danger
                      loading={completeMutation.isPending}
                      onClick={() => completeMutation.mutate(t.id)}
                    >
                      拣完
                    </Button>
                  )
                }
              >
                <List.Item.Meta
                  avatar={<Text strong style={{ fontSize: 18 }}>{index + 1}</Text>}
                  title={
                    <Space size={6} wrap>
                      <Tag color="geekblue">{t.locationCode ?? '未定位'}</Tag>
                      <span className="text-sm">{t.materialCode ?? t.materialId}</span>
                      {t.batchNo ? <Tag style={{ marginInlineEnd: 0 }}>{t.batchNo}</Tag> : null}
                    </Space>
                  }
                  description={
                    <span className="text-xs text-gray-500">
                      数量 <Text strong>{fmtQty(t.qty)}</Text>
                      {t.sourceNo ? ` · ${t.sourceNo}` : ''}
                      {` · ${TASK_STATUS_MAP[t.status] ?? t.status}`}
                    </span>
                  }
                />
              </List.Item>
            )
          }}
        />
      </Card>

      <Button
        block
        size="large"
        type="primary"
        danger
        style={{ marginTop: 12 }}
        disabled={!allDone || wave?.status === 20}
        loading={completeWaveMutation.isPending}
        onClick={() => completeWaveMutation.mutate()}
      >
        完成波次{allDone ? '' : '（需全部任务完成）'}
      </Button>
      <p className="mt-3 text-xs text-gray-500">
        拣选路径 = 库位编码升序；出库账面已在出库单审核时扣减，此处为实物拣选确认。
      </p>
    </MobileShell>
  )
}
