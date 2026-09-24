import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Card, Select, Table, Tooltip, Empty } from 'antd'
import PageHeader from '@/components/PageHeader'
import { getWorkorders, type Workorder } from '@/api/plan'
import { getSchedules as fetchSchedules, type ScheduleVO } from '@/api/plan'
import { get } from '@/api/request'

interface Workcenter { id: number; code: string; name: string }

/** 工序排程甘特页：工作中心时间轴 + 时间条（手动排程数据的可视化，拖拽编辑随 P4 呈现层补） */
export default function ScheduleGantt() {
  const [wcId, setWcId] = useState<number | null>(null)

  const { data: workcenters } = useQuery({
    queryKey: ['basedata', 'workcenters', 'gantt'],
    queryFn: () => get<Workcenter[]>('/basedata/workcenters/enabled'),
  })
  const { data: schedules, isLoading } = useQuery({
    queryKey: ['plan', 'schedules', wcId],
    queryFn: () => fetchSchedules(wcId!),
    enabled: wcId != null,
  })
  const { data: workorders } = useQuery({
    queryKey: ['plan', 'workorders', 'gantt'],
    queryFn: () => getWorkorders({ pageNum: 1, pageSize: 200 }),
  })

  // 时间轴：首尾时间 8 小时窗口按分钟铺开
  const { startMs, spanMs } = useMemo(() => {
    const items = schedules ?? []
    if (items.length === 0) {
      return { startMs: Date.now() - 4 * 3600_000, spanMs: 8 * 3600_000 }
    }
    const times = items.flatMap((s) => [new Date(s.planStartTime ?? Date.now()).getTime(), new Date(s.planEndTime ?? Date.now()).getTime()])
    const min = Math.min(...times)
    const max = Math.max(...times, min + 3600_000)
    return { startMs: min - 1800_000, spanMs: max - min + 3600_000 }
  }, [schedules])

  const rows: { key: string; name: string; bars: { id: number; leftPct: number; widthPct: number; label: string; start: string; end: string }[] }[] = []
  ;(schedules ?? []).forEach((s: ScheduleVO) => {
    const wo = (workorders?.records ?? []).find((w: Workorder) => w.id === s.workorderId)
    const name = `工单 ${wo?.woNo ?? s.workorderId}（工序#${s.woStepId}）`
    const st = new Date(s.planStartTime ?? Date.now()).getTime()
    const et = new Date(s.planEndTime ?? st + 3600_000).getTime()
    rows.push({
      key: String(s.id),
      name,
      bars: [{
        id: s.id,
        leftPct: ((st - startMs) / spanMs) * 100,
        widthPct: Math.max(2, ((et - st) / spanMs) * 100),
        label: s.lockFlag === 1 ? '🔒' : '',
        start: s.planStartTime ?? '-',
        end: s.planEndTime ?? '-',
      }],
    })
  })

  return (
    <>
      <PageHeader title="排程甘特" subtitle="工作中心时间片可视化（数据来自手动排程 /plan/schedules；拖拽编辑随 P4 呈现层建设）" />
      <Card>
        <Select
          showSearch
          optionFilterProp="label"
          placeholder="选择工作中心"
          style={{ width: 240, marginBottom: 16 }}
          onChange={(v) => setWcId(v)}
          options={(workcenters ?? []).map((w) => ({ value: w.id, label: w.name }))}
        />
        {wcId == null || (schedules ?? []).length === 0 ? (
          <Empty description={wcId == null ? '请选择工作中心' : '该工作中心暂无排程时间片'} />
        ) : (
          <Table
            loading={isLoading}
            pagination={false}
            dataSource={rows}
            columns={[
              { title: '工序任务', dataIndex: 'name', width: 260 },
              {
                title: '时间轴',
                render: (_, row: (typeof rows)[number]) => (
                  <div style={{ position: 'relative', height: 28, background: '#f5f7fa', borderRadius: 4 }}>
                    {row.bars.map((b) => (
                      <Tooltip key={b.id} title={`${b.start} ~ ${b.end}`}>
                        <div
                          style={{
                            position: 'absolute',
                            left: `${b.leftPct}%`,
                            width: `${b.widthPct}%`,
                            top: 4,
                            height: 20,
                            borderRadius: 4,
                            background: 'linear-gradient(90deg,#1668dc,#36cfc9)',
                            color: '#fff',
                            fontSize: 12,
                            lineHeight: '20px',
                            textAlign: 'center',
                            overflow: 'hidden',
                          }}
                        >
                          {b.label}
                        </div>
                      </Tooltip>
                    ))}
                  </div>
                ),
              },
            ]}
          />
        )}
      </Card>
    </>
  )
}
