import { useMemo, useState } from 'react'
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
  Select,
  Space,
  Tag,
  message,
} from 'antd'
import { LogoutOutlined, SearchOutlined } from '@ant-design/icons'
import { MobileShell, ScanCameraButton } from '@/mobile/shared'
import { useAuthStore } from '@/stores/auth'
import {
  auditOtherIn,
  auditOtherOut,
  completeCheck,
  createCheck,
  createOtherIn,
  createOtherOut,
  fetchBatches,
  fetchCheckDetail,
  fetchMaterialOptions,
  fetchWarehouseOptions,
  fmtQty,
  saveCheckActual,
  type BatchRow,
  type CheckDetailLine,
  type MaterialOption,
  type WarehouseOption,
} from '@/api/inventory'

/** 作业类型：receive 收货（其他入库快速单）/ pick 拣货（其他出库+批次FIFO建议）/ check 盘点（快照录差异） */
type ScanType = 'receive' | 'pick' | 'check'

const TYPE_META: Record<ScanType, { title: string; hint: string }> = {
  receive: { title: '扫码收货', hint: '扫物料条码/输编码 → 录批次与数量 → 提交其他入库单并审核' },
  pick: { title: '扫码拣货', hint: '扫物料条码/输编码 → 查批次(FIFO建议) → 录数量 → 提交其他出库单并审核' },
  check: { title: '扫码盘点', hint: '选仓库生成快照 → 逐行录实盘 → 完成盘点自动差异调平' },
}

/** 按 编码/条码/名称前缀 匹配物料；扫码内容通常等于编码或条码 */
async function findMaterial(code: string): Promise<MaterialOption | null> {
  const list = await fetchMaterialOptions(code)
  if (list.length === 0) return null
  const c = code.trim().toLowerCase()
  return (
    list.find((m) => m.code.toLowerCase() === c || (m.barcode ?? '').toLowerCase() === c) ??
    list[0]
  )
}

/**
 * 移动端扫码作业页（/m/scan?type=receive|pick|check）。
 * 全部复用桌面端既有接口（other-in / other-out / checks），不新增后端接口。
 * 核心交互为「手工输入+查询」；摄像头扫码为可选增强（ScanCameraButton）。
 */
export default function MobileScan() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const logout = useAuthStore((s) => s.logout)
  const user = useAuthStore((s) => s.user)

  const type = (['receive', 'pick', 'check'].includes(params.get('type') ?? '')
    ? params.get('type')
    : 'receive') as ScanType
  const meta = TYPE_META[type]

  // ---- 公共状态 ----
  const [warehouseId, setWarehouseId] = useState<number | undefined>(undefined)
  const [codeInput, setCodeInput] = useState('')

  // ---- 收货 / 拣货状态 ----
  const [material, setMaterial] = useState<MaterialOption | null>(null)
  const [batchNo, setBatchNo] = useState('')
  const [qty, setQty] = useState<number | null>(null)

  // ---- 盘点状态 ----
  const [checkId, setCheckId] = useState<number | null>(null)
  const [actualMap, setActualMap] = useState<Record<number, number>>({})

  // ---- 选项 ----
  const { data: warehouses } = useQuery({
    queryKey: ['inventory', 'warehouseOptions'],
    queryFn: fetchWarehouseOptions,
    staleTime: 5 * 60 * 1000,
  })
  const whOptions = (warehouses ?? []).map((w: WarehouseOption) => ({
    label: `${w.code} ${w.name}`,
    value: w.id,
  }))

  // ---- 物料匹配（扫码/输入后点查询） ----
  const searchMutation = useMutation({
    mutationFn: findMaterial,
    onSuccess: (m) => {
      if (!m) {
        setMaterial(null)
        message.warning('未匹配到物料，请确认编码/条码')
        return
      }
      setMaterial(m)
      setBatchNo('')
      setQty(null)
    },
  })

  // ---- 拣货：批次 FIFO 建议（接口按首入时间升序返回） ----
  const { data: batches, isFetching: batchesLoading } = useQuery({
    queryKey: ['inventory', 'mobileBatches', material?.id, warehouseId],
    queryFn: () => fetchBatches(material?.id as number, warehouseId),
    enabled: type === 'pick' && material != null,
  })
  const fifoTotal = useMemo(
    () => (batches ?? []).reduce((s, b: BatchRow) => s + b.qty, 0),
    [batches],
  )

  // ---- 盘点明细 ----
  const { data: checkDetail } = useQuery({
    queryKey: ['inventory', 'mobileCheck', checkId],
    queryFn: () => fetchCheckDetail(checkId as number),
    enabled: type === 'check' && checkId != null,
  })

  // ---- 提交：收货 = 其他入库快速单（创建+审核一次完成） ----
  const receiveMutation = useMutation({
    mutationFn: async () => {
      const r = await createOtherIn({
        warehouseId: warehouseId as number,
        reason: '移动端扫码收货',
        lines: [{ materialId: material?.id as number, batchNo: batchNo.trim() || undefined, qty: qty as number }],
      })
      await auditOtherIn(r.id)
      return r.id
    },
    onSuccess: () => {
      message.success('收货入库已过账')
      setMaterial(null)
      setCodeInput('')
      setBatchNo('')
      setQty(null)
      queryClient.invalidateQueries({ queryKey: ['inventory'] })
    },
  })

  // ---- 提交：拣货 = 其他出库（默认 FIFO；选批次则指定批次出库） ----
  const pickMutation = useMutation({
    mutationFn: async () => {
      const r = await createOtherOut({
        warehouseId: warehouseId as number,
        reason: '移动端扫码拣货',
        lines: [{ materialId: material?.id as number, batchNo: batchNo.trim() || undefined, qty: qty as number }],
      })
      await auditOtherOut(r.id)
      return r.id
    },
    onSuccess: () => {
      message.success('拣货出库已过账')
      setMaterial(null)
      setCodeInput('')
      setBatchNo('')
      setQty(null)
      queryClient.invalidateQueries({ queryKey: ['inventory'] })
    },
  })

  // ---- 盘点：生成快照 → 录实盘 → 完成 ----
  const startCheckMutation = useMutation({
    mutationFn: () => createCheck({ warehouseId: warehouseId as number, remark: '移动端盘点' }),
    onSuccess: (r) => {
      setCheckId(r.id)
      setActualMap({})
      message.success('盘点单已生成（库存快照）')
    },
  })

  const saveActualMutation = useMutation({
    mutationFn: () => {
      const lines = (checkDetail?.lines ?? [])
        .filter((l: CheckDetailLine) => actualMap[l.id] != null)
        .map((l: CheckDetailLine) => ({ detailId: l.id, actualQty: actualMap[l.id] }))
      return saveCheckActual(checkId as number, lines)
    },
    onSuccess: () => {
      message.success('实盘已保存（可继续录入）')
      queryClient.invalidateQueries({ queryKey: ['inventory', 'mobileCheck', checkId] })
    },
  })

  const completeCheckMutation = useMutation({
    mutationFn: () => completeCheck(checkId as number),
    onSuccess: (r) => {
      message.success(`盘点完成，差异调平 ${r.adjusted} 行`)
      setCheckId(null)
      setActualMap({})
      queryClient.invalidateQueries({ queryKey: ['inventory'] })
    },
  })

  // 收货时批次物料必须填批次号；拣货的批次号为可选（空=FIFO 自动分配）
  const batchRequiredForIn =
    type === 'receive' && material != null && Number(material.batchManaged ?? 0) === 1
  const canSubmit =
    warehouseId != null &&
    material != null &&
    qty != null &&
    qty > 0 &&
    (!batchRequiredForIn || batchNo.trim() !== '')

  return (
    <MobileShell
      title={meta.title}
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
      {/* 作业类型切换 */}
      <Segmented
        block
        value={type}
        onChange={(v) => setParams({ type: v as string })}
        options={[
          { label: '收货', value: 'receive' },
          { label: '拣货', value: 'pick' },
          { label: '盘点', value: 'check' },
        ]}
        style={{ marginBottom: 12 }}
      />
      <p className="mb-3 text-xs text-gray-500">{meta.hint}</p>

      <Card size="small" style={{ marginBottom: 12 }}>
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
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

          {type !== 'check' && (
            <>
              <Input.Search
                size="large"
                value={codeInput}
                onChange={(e) => setCodeInput(e.target.value)}
                placeholder="扫物料条码 / 输入物料编码"
                enterButton={<SearchOutlined />}
                loading={searchMutation.isPending}
                onSearch={(v) => v.trim() && searchMutation.mutate(v.trim())}
              />
              <ScanCameraButton
                onDetected={(code) => {
                  setCodeInput(code)
                  searchMutation.mutate(code)
                }}
              />
            </>
          )}
        </Space>
      </Card>

      {/* ==================== 收货 / 拣货 ==================== */}
      {type !== 'check' && material && (
        <Card size="small" title="物料信息" style={{ marginBottom: 12 }}>
          <Descriptions size="small" column={1} bordered>
            <Descriptions.Item label="编码">{material.code}</Descriptions.Item>
            <Descriptions.Item label="名称">{material.name}</Descriptions.Item>
            <Descriptions.Item label="批次管理">
              {material.batchManaged ? <Tag color="blue">批次物料</Tag> : <Tag>普通物料</Tag>}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {type === 'receive' && material && (
        <Card size="small" title="收货录入" style={{ marginBottom: 12 }}>
          <Space direction="vertical" style={{ width: '100%' }} size={10}>
            {material.batchManaged ? (
              <Input
                size="large"
                placeholder="批次号（批次物料必填，如 B20260905-1）"
                value={batchNo}
                maxLength={50}
                onChange={(e) => setBatchNo(e.target.value)}
              />
            ) : (
              <Alert type="info" showIcon message="普通物料：无需批次号，直接录数量" />
            )}
            <InputNumber
              size="large"
              style={{ width: '100%' }}
              placeholder="收货数量"
              min={0.0001}
              precision={4}
              value={qty}
              onChange={(v) => setQty(v)}
            />
            <Button
              type="primary"
              size="large"
              block
              loading={receiveMutation.isPending}
              disabled={!canSubmit}
              onClick={() => receiveMutation.mutate()}
            >
              提交收货（其他入库+审核）
            </Button>
          </Space>
        </Card>
      )}

      {type === 'pick' && material && (
        <Card size="small" title="批次库存（FIFO 建议，由早到晚）" style={{ marginBottom: 12 }} loading={batchesLoading}>
          <List
            size="small"
            dataSource={batches ?? []}
            locale={{ emptyText: '该物料在此仓库无批次库存' }}
            renderItem={(b: BatchRow) => (
              <List.Item
                extra={<span className="font-medium">{fmtQty(b.qty)}</span>}
                onClick={() => {
                  setBatchNo(b.batchNo)
                  setQty(b.qty)
                }}
                style={{ cursor: 'pointer', background: batchNo === b.batchNo ? '#e6f4ff' : undefined }}
              >
                <List.Item.Meta
                  title={
                    <Space size={6}>
                      <span className="font-mono">{b.batchNo}</span>
                      <Tag style={{ marginInlineEnd: 0 }}>库龄 {b.ageDays ?? '-'} 天</Tag>
                    </Space>
                  }
                  description={`首入 ${b.firstInAt ?? '-'}${b.warehouseName ? ' · ' + b.warehouseName : ''}`}
                />
              </List.Item>
            )}
          />
          <p className="mb-2 text-xs text-gray-500">
            批次合计 {fmtQty(fifoTotal)}；点击批次行=指定该批次出库并带入数量；不选批次=按 FIFO 自动分配。
          </p>
          <Space direction="vertical" style={{ width: '100%' }} size={10}>
            {material.batchManaged ? (
              <Input
                size="large"
                placeholder="指定批次号（留空=FIFO 自动分配）"
                value={batchNo}
                maxLength={50}
                onChange={(e) => setBatchNo(e.target.value)}
              />
            ) : (
              <Alert type="info" showIcon message="普通物料：汇总出库，无需批次" />
            )}
            <InputNumber
              size="large"
              style={{ width: '100%' }}
              placeholder="出库数量"
              min={0.0001}
              precision={4}
              value={qty}
              onChange={(v) => setQty(v)}
            />
            <Button
              type="primary"
              size="large"
              block
              danger
              loading={pickMutation.isPending}
              disabled={!canSubmit || (material.batchManaged === 1 && fifoTotal <= 0 && !batchNo)}
              onClick={() => pickMutation.mutate()}
            >
              提交拣货（其他出库+审核）
            </Button>
          </Space>
        </Card>
      )}

      {/* ==================== 盘点 ==================== */}
      {type === 'check' && (
        <Card size="small" style={{ marginBottom: 12 }}>
          {checkId == null ? (
            <Button
              type="primary"
              size="large"
              block
              loading={startCheckMutation.isPending}
              disabled={warehouseId == null}
              onClick={() => startCheckMutation.mutate()}
            >
              开始盘点（生成库存快照）
            </Button>
          ) : (
            <>
              <div className="mb-2 text-sm font-medium">盘点单 #{checkDetail?.no ?? checkId}</div>
              <List
                size="small"
                dataSource={checkDetail?.lines ?? []}
                locale={{ emptyText: '快照明细为空（该仓库无库存）' }}
                renderItem={(l: CheckDetailLine) => (
                  <List.Item>
                    <Space direction="vertical" size={2} style={{ flex: 1 }}>
                      <Space size={6} wrap>
                        <span className="font-medium">{l.materialName ?? l.materialId}</span>
                        {l.batchNo ? <Tag color="blue">{l.batchNo}</Tag> : null}
                      </Space>
                      <span className="text-xs text-gray-500">
                        账面 {fmtQty(l.bookQty)}
                        {l.actualQty != null ? ` · 实盘 ${fmtQty(l.actualQty)} · 差异 ${fmtQty(l.diffQty)}` : ' · 未盘'}
                      </span>
                    </Space>
                    <InputNumber
                      size="small"
                      style={{ width: 110 }}
                      min={0}
                      precision={4}
                      placeholder="实盘"
                      value={actualMap[l.id] ?? (l.actualQty != null ? l.actualQty : undefined)}
                      onChange={(v) => setActualMap((m) => ({ ...m, [l.id]: v as number }))}
                    />
                  </List.Item>
                )}
              />
              <Space direction="vertical" style={{ width: '100%', marginTop: 8 }} size={8}>
                <Button
                  size="large"
                  block
                  loading={saveActualMutation.isPending}
                  disabled={Object.keys(actualMap).length === 0}
                  onClick={() => saveActualMutation.mutate()}
                >
                  保存实盘（可分批）
                </Button>
                <Button
                  type="primary"
                  size="large"
                  block
                  loading={completeCheckMutation.isPending}
                  onClick={() =>
                    Modal.confirm({
                      title: '完成盘点？',
                      content: '已录差异行将生成 ADJUST 流水调平库存，未录行不调平。',
                      onOk: () => completeCheckMutation.mutate(),
                    })
                  }
                >
                  完成盘点（差异调平）
                </Button>
              </Space>
            </>
          )}
        </Card>
      )}
    </MobileShell>
  )
}
