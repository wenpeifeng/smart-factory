import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Button,
  Card,
  Input,
  List,
  Segmented,
  Select,
  Space,
  Tag,
  Typography,
  message,
} from 'antd'
import { LogoutOutlined, SearchOutlined } from '@ant-design/icons'
import { MobileShell, ScanCameraButton } from '@/mobile/shared'
import { useAuthStore } from '@/stores/auth'
import {
  SERIAL_STATUS_MAP,
  fetchMaterialOptions,
  fetchSerialPage,
  fetchWarehouseOptions,
  registerSerials,
  writeOffSerials,
  type MaterialOption,
} from '@/api/inventory'

const { Text } = Typography

type Mode = 'in' | 'out' | 'query'

/**
 * 移动端序列号扫码页（/m/wms/serials）：入库登记 / 出库核销 / 查询追溯。
 * 核心交互=扫码枪或手工输入逐条累计，摄像头扫码为可选增强。
 */
export default function SerialScan() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const logout = useAuthStore((s) => s.logout)
  const user = useAuthStore((s) => s.user)

  const [mode, setMode] = useState<Mode>('in')
  const [code, setCode] = useState('')
  const [collected, setCollected] = useState<string[]>([])
  const [materialId, setMaterialId] = useState<number | undefined>(undefined)
  const [warehouseId, setWarehouseId] = useState<number | undefined>(undefined)
  const [batchNo, setBatchNo] = useState('')
  const [docNo, setDocNo] = useState('')
  const [queryNo, setQueryNo] = useState('')

  const { data: materials } = useQuery({
    queryKey: ['inventory', 'materialOptions'],
    queryFn: () => fetchMaterialOptions(),
    staleTime: 5 * 60 * 1000,
  })
  const matOptions = (materials ?? []).map((m: MaterialOption) => ({
    label: `${m.code} ${m.name}`,
    value: m.id,
  }))
  const { data: warehouses } = useQuery({
    queryKey: ['inventory', 'warehouseOptions'],
    queryFn: fetchWarehouseOptions,
    staleTime: 5 * 60 * 1000,
  })
  const whOptions = (warehouses ?? []).map((w) => ({ label: `${w.code} ${w.name}`, value: w.id }))

  const { data: queryResult, isFetching: queryLoading } = useQuery({
    queryKey: ['inventory', 'serialQuery', queryNo],
    queryFn: () => fetchSerialPage({ serialNo: queryNo, pageSize: 20 }),
    enabled: mode === 'query' && queryNo.trim() !== '',
  })

  const registerMutation = useMutation({
    mutationFn: () =>
      registerSerials({
        materialId: materialId as number,
        warehouseId: warehouseId as number,
        batchNo: batchNo.trim() || undefined,
        sourceNo: docNo.trim() || undefined,
        serialNos: collected,
      }),
    onSuccess: (r) => {
      message.success(`登记成功 ${r.registered} 条（IN_STOCK）`)
      setCollected([])
      queryClient.invalidateQueries({ queryKey: ['inventory'] })
    },
  })

  const writeOffMutation = useMutation({
    mutationFn: () => writeOffSerials({ outNo: docNo.trim() || undefined, serialNos: collected }),
    onSuccess: (r) => {
      message.success(`核销成功 ${r.writtenOff} 条（OUT）`)
      setCollected([])
      queryClient.invalidateQueries({ queryKey: ['inventory'] })
    },
  })

  /** 扫码/回车：入收集列表（去重） */
  const collect = (value: string) => {
    const v = value.trim()
    if (!v) return
    setCollected((list) => (list.includes(v) ? list : [...list, v]))
    setCode('')
  }

  const canSubmit =
    collected.length > 0 &&
    (mode === 'out' || (materialId != null && warehouseId != null))

  return (
    <MobileShell
      title="序列号扫码"
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
        value={mode}
        onChange={(v) => {
          setMode(v as Mode)
          setCollected([])
        }}
        options={[
          { label: '入库登记', value: 'in' },
          { label: '出库核销', value: 'out' },
          { label: '查询追溯', value: 'query' },
        ]}
        style={{ marginBottom: 12 }}
      />

      {mode === 'query' ? (
        <Card size="small">
          <Input.Search
            size="large"
            value={queryNo}
            onChange={(e) => setQueryNo(e.target.value)}
            placeholder="扫序列号 / 输入序列号（支持前缀）"
            enterButton={<SearchOutlined />}
            loading={queryLoading}
            onSearch={(v) => setQueryNo(v)}
            allowClear
          />
          <ScanCameraButton onDetected={(v) => setQueryNo(v)} />
          <List
            size="small"
            className="mt-2"
            dataSource={queryResult?.records ?? []}
            locale={{ emptyText: '未录入或未扫到序列号' }}
            renderItem={(s) => (
              <List.Item
                extra={<Tag color={s.status === 'IN_STOCK' ? 'success' : s.status === 'OUT' ? 'default' : 'error'}>{SERIAL_STATUS_MAP[s.status] ?? s.status}</Tag>}
              >
                <List.Item.Meta
                  title={<span className="font-mono text-sm">{s.serialNo}</span>}
                  description={
                    <span className="text-xs text-gray-500">
                      {s.materialCode ?? s.materialId} · 入 {s.sourceNo ?? '-'} · 出 {s.outNo ?? '-'}
                    </span>
                  }
                />
              </List.Item>
            )}
          />
        </Card>
      ) : (
        <>
          <Card size="small" style={{ marginBottom: 12 }}>
            <Space direction="vertical" style={{ width: '100%' }} size={10}>
              {mode === 'in' && (
                <>
                  <Select
                    size="large"
                    style={{ width: '100%' }}
                    placeholder="选择物料"
                    showSearch
                    optionFilterProp="label"
                    options={matOptions}
                    value={materialId}
                    onChange={setMaterialId}
                  />
                  <Select
                    size="large"
                    style={{ width: '100%' }}
                    placeholder="选择仓库"
                    showSearch
                    optionFilterProp="label"
                    options={whOptions}
                    value={warehouseId}
                    onChange={setWarehouseId}
                  />
                  <Input
                    size="large"
                    placeholder="批次号（可选）"
                    maxLength={50}
                    value={batchNo}
                    onChange={(e) => setBatchNo(e.target.value)}
                  />
                </>
              )}
              <Input
                size="large"
                placeholder={mode === 'in' ? '入库来源单号（可选，如 RK...）' : '出库单号（可选，如 XS...）'}
                maxLength={40}
                value={docNo}
                onChange={(e) => setDocNo(e.target.value)}
              />
              <Input.Search
                size="large"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                placeholder="扫序列号 / 输入后回车累计"
                enterButton="计入"
                onSearch={collect}
              />
              <ScanCameraButton onDetected={collect} />
            </Space>
          </Card>

          <Card
            size="small"
            title={`已扫 ${collected.length} 条`}
            extra={
              collected.length > 0 ? (
                <Button size="small" type="link" danger onClick={() => setCollected([])}>
                  清空
                </Button>
              ) : null
            }
          >
            <List
              size="small"
              dataSource={collected}
              locale={{ emptyText: '暂未扫码（扫码枪输入回车自动计入）' }}
              renderItem={(sn) => (
                <List.Item
                  extra={
                    <Button
                      size="small"
                      type="link"
                      danger
                      onClick={() => setCollected((list) => list.filter((x) => x !== sn))}
                    >
                      移除
                    </Button>
                  }
                >
                  <span className="font-mono text-sm">{sn}</span>
                </List.Item>
              )}
            />
            {mode === 'out' && (
              <Alert
                className="mt-2"
                type="info"
                showIcon
                message="核销要求全部序列号为在库状态，任一不符整体回滚"
              />
            )}
            <Button
              block
              size="large"
              type="primary"
              danger={mode === 'out'}
              style={{ marginTop: 12 }}
              disabled={!canSubmit}
              loading={registerMutation.isPending || writeOffMutation.isPending}
              onClick={() => (mode === 'in' ? registerMutation.mutate() : writeOffMutation.mutate())}
            >
              {mode === 'in' ? `提交登记（${collected.length} 条）` : `提交核销（${collected.length} 条）`}
            </Button>
          </Card>
          <p className="mt-3 text-xs text-gray-500">
            <Text type="secondary">
              序列号只记追溯台账，库存数量账由对应出入库单审核过账。
            </Text>
          </p>
        </>
      )}
    </MobileShell>
  )
}
