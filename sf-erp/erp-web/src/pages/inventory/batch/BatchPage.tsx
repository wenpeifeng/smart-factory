import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  BIZ_TYPE_MAP,
  fetchBatches,
  fetchBackwardTrace,
  fetchForwardTrace,
  fetchMaterialOptions,
  fetchWarehouseOptions,
  fmtQty,
  type BatchRow,
  type MaterialOption,
  type TraceNode,
  type WarehouseOption,
} from '@/api/inventory'

const { Title } = Typography

/**
 * 批次追溯页（二期）：
 * 1. 选物料（可再按仓库过滤）→ 批次余额清单（批次/数量/首入时间/库龄天，FIFO 顺序）；
 * 2. 点行抽屉展示该批次 正溯（去向）/ 反溯（来源）流水，bizType/bizNo 可读，
 *    来源单据详情由对应模块按单号查询（本页不跨模块取数）。
 */
export default function BatchPage() {
  const [materialId, setMaterialId] = useState<number | undefined>(undefined)
  const [warehouseId, setWarehouseId] = useState<number | undefined>(undefined)
  const [batchFilter, setBatchFilter] = useState('')
  const [activeBatch, setActiveBatch] = useState<BatchRow | null>(null)
  const [traceTab, setTraceTab] = useState('backward')

  // ---- 下拉选项（基础资料只读接口） ----
  const { data: warehouses } = useQuery({
    queryKey: ['inventory', 'warehouseOptions'],
    queryFn: fetchWarehouseOptions,
    staleTime: 5 * 60 * 1000,
  })
  const whOptions = (warehouses ?? []).map((w: WarehouseOption) => ({
    label: `${w.code} ${w.name}`,
    value: w.id,
  }))

  const { data: materialOptions } = useQuery({
    queryKey: ['inventory', 'materialOptions'],
    queryFn: () => fetchMaterialOptions(),
    staleTime: 5 * 60 * 1000,
  })
  // 批次管理物料排前，普通物料也可查（结果为空时给空态提示）
  const matOptions = (materialOptions ?? [])
    .slice()
    .sort((a: MaterialOption, b: MaterialOption) => (b.batchManaged ?? 0) - (a.batchManaged ?? 0))
    .map((m: MaterialOption) => ({
      label: `${m.batchManaged ? '[批] ' : ''}${m.code} ${m.name}`,
      value: m.id,
    }))

  // ---- 批次余额清单 ----
  const {
    data: batches,
    isLoading,
    isFetching,
    isError,
  } = useQuery({
    queryKey: ['inventory', 'batches', materialId, warehouseId],
    queryFn: () => fetchBatches(materialId as number, warehouseId),
    enabled: materialId != null,
  })

  // ---- 追溯明细（正溯/反溯共用一个抽屉，按 tab 懒查询） ----
  const enabledTrace = activeBatch != null && materialId != null
  const { data: backward, isLoading: bwdLoading } = useQuery({
    queryKey: ['inventory', 'trace', 'backward', materialId, activeBatch?.batchNo],
    queryFn: () => fetchBackwardTrace(materialId as number, activeBatch?.batchNo as string),
    enabled: enabledTrace && traceTab === 'backward',
  })
  const { data: forward, isLoading: fwdLoading } = useQuery({
    queryKey: ['inventory', 'trace', 'forward', materialId, activeBatch?.batchNo],
    queryFn: () => fetchForwardTrace(materialId as number, activeBatch?.batchNo as string),
    enabled: enabledTrace && traceTab === 'forward',
  })

  const columns: ColumnsType<BatchRow> = [
    { title: '仓库', dataIndex: 'warehouseName', width: 140, render: (v: string | null) => v ?? '-' },
    {
      title: '物料',
      width: 220,
      render: (_, r) => (
        <Space size={4}>
          <span>{r.materialName ?? '-'}</span>
          {r.materialCode && <Tag style={{ marginInlineEnd: 0 }}>{r.materialCode}</Tag>}
        </Space>
      ),
    },
    { title: '批次号', dataIndex: 'batchNo', width: 170 },
    { title: '现存量', dataIndex: 'qty', width: 110, align: 'right', render: (v: number) => fmtQty(v) },
    { title: '首次入库', dataIndex: 'firstInAt', width: 170, render: (v: string | null) => v ?? '-' },
    {
      title: '库龄(天)',
      dataIndex: 'ageDays',
      width: 100,
      align: 'right',
      render: (v: number | null) => (v == null ? '-' : <Tag color={v >= 90 ? 'error' : v >= 30 ? 'warning' : 'default'}>{v}</Tag>),
    },
    {
      title: '操作',
      key: 'actions',
      width: 110,
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => setActiveBatch(record)}>
          追溯
        </Button>
      ),
    },
  ]

  const traceColumns: ColumnsType<TraceNode> = [
    {
      title: '业务类型',
      dataIndex: 'bizType',
      width: 110,
      render: (v: string) => <Tag color={v.endsWith('OUT') || v.endsWith('RET') || v === 'PROD_USE' ? 'volcano' : 'green'}>{BIZ_TYPE_MAP[v] ?? v}</Tag>,
    },
    { title: '单号', dataIndex: 'bizNo', width: 170, render: (v: string | null) => v ?? '-' },
    { title: '仓库', dataIndex: 'warehouseName', width: 130, render: (v: string | null) => v ?? '-' },
    {
      title: '数量',
      dataIndex: 'qty',
      width: 110,
      align: 'right',
      render: (v: number) => (
        <span style={{ color: v > 0 ? '#389e0d' : '#cf1322' }}>{fmtQty(v)}</span>
      ),
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v: string | null) => v ?? '-' },
    { title: '时间', dataIndex: 'createTime', width: 170 },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        批次追溯
      </Title>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Select
            style={{ width: 280 }}
            placeholder="选择物料（[批]=批次管理物料）"
            allowClear
            showSearch
            optionFilterProp="label"
            options={matOptions}
            value={materialId}
            onChange={(v) => {
              setMaterialId(v)
              setActiveBatch(null)
            }}
          />
          <Select
            style={{ width: 200 }}
            placeholder="全部仓库"
            allowClear
            showSearch
            optionFilterProp="label"
            options={whOptions}
            value={warehouseId}
            onChange={setWarehouseId}
          />
          <Input.Search
            style={{ width: 200 }}
            placeholder="批次号过滤"
            allowClear
            onSearch={(v) => setBatchFilter(v)}
          />
        </Space>

        {isError ? (
          <Alert type="error" showIcon message="批次查询失败，请稍后重试" style={{ marginBottom: 12 }} />
        ) : null}

        <Table<BatchRow>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={(batches ?? []).filter((b) => !batchFilter || b.batchNo.includes(batchFilter))}
          loading={isLoading || isFetching}
          locale={{
            emptyText:
              materialId == null ? (
                <Empty description="请先选择物料查看批次余额" />
              ) : (
                <Empty description="该物料暂无批次库存（非批次物料不产生批次行）" />
              ),
          }}
          pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条` }}
        />
      </Card>

      <Drawer
        title={`批次追溯 ${activeBatch?.batchNo ?? ''}`}
        width={760}
        open={activeBatch != null}
        onClose={() => setActiveBatch(null)}
      >
        {activeBatch && (
          <>
            <Descriptions size="small" column={2} bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label="物料">
                {activeBatch.materialName ?? '-'}（{activeBatch.materialCode ?? '-'}）
              </Descriptions.Item>
              <Descriptions.Item label="仓库">{activeBatch.warehouseName ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="现存量">{fmtQty(activeBatch.qty)}</Descriptions.Item>
              <Descriptions.Item label="首次入库">{activeBatch.firstInAt ?? '-'}</Descriptions.Item>
            </Descriptions>
            <Tabs
              activeKey={traceTab}
              onChange={setTraceTab}
              items={[
                {
                  key: 'backward',
                  label: `反溯 · 来源（${backward?.length ?? 0}）`,
                  children: (
                    <Table<TraceNode>
                      rowKey="id"
                      size="small"
                      columns={traceColumns}
                      dataSource={backward ?? []}
                      loading={bwdLoading}
                      pagination={false}
                      locale={{ emptyText: '无入库流水' }}
                    />
                  ),
                },
                {
                  key: 'forward',
                  label: `正溯 · 去向（${forward?.length ?? 0}）`,
                  children: (
                    <Table<TraceNode>
                      rowKey="id"
                      size="small"
                      columns={traceColumns}
                      dataSource={forward ?? []}
                      loading={fwdLoading}
                      pagination={false}
                      locale={{ emptyText: '无出库流水（批次未被消耗）' }}
                    />
                  ),
                },
              ]}
            />
          </>
        )}
      </Drawer>
    </div>
  )
}
