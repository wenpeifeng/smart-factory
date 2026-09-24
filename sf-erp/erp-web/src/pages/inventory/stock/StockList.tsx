import { useState } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { Alert, Button, Card, Input, Select, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  fetchStockPage,
  fetchWarningPage,
  fetchWarehouseOptions,
  fmtQty,
  type StockRow,
  type WarehouseOption,
} from '@/api/inventory'

const { Title } = Typography

/** 列定义：低于安全库存的行整行红色高亮（见 onRow），状态列再给 Tag 提示 */
const COLUMNS: ColumnsType<StockRow> = [
  {
    title: '仓库',
    dataIndex: 'warehouseName',
    width: 150,
    render: (_, r) => (
      <Space size={4}>
        <span>{r.warehouseName ?? '-'}</span>
        {r.warehouseCode && <Tag style={{ marginInlineEnd: 0 }}>{r.warehouseCode}</Tag>}
      </Space>
    ),
  },
  {
    title: '物料编码',
    dataIndex: 'materialCode',
    width: 130,
    render: (v: string | null) => v ?? '-',
  },
  {
    title: '物料名称',
    dataIndex: 'materialName',
    ellipsis: true,
    render: (v: string | null) => v ?? '-',
  },
  {
    title: '现存量',
    dataIndex: 'qty',
    width: 120,
    align: 'right',
    render: (v: number) => fmtQty(v),
  },
  {
    title: '安全库存',
    dataIndex: 'safetyStock',
    width: 110,
    align: 'right',
    render: (v: number | null) => fmtQty(v),
  },
  {
    title: '库存状态',
    dataIndex: 'belowSafety',
    width: 130,
    render: (below: boolean, r) =>
      below ? (
        <Tag color="error">低于安全库存</Tag>
      ) : (
        <Tag color={r.qty === 0 ? 'warning' : 'success'}>{r.qty === 0 ? '零库存' : '充足'}</Tag>
      ),
  },
  { title: '更新时间', dataIndex: 'updateTime', width: 170, render: (v: string | null) => v ?? '-' },
]

/**
 * 即时库存页：聚合多列筛选（仓库/物料关键词/零库存/仅预警）
 * + 低于安全库存的行红色高亮 + 顶部预警汇总条。
 */
export default function StockList() {
  const [warehouseId, setWarehouseId] = useState<number | undefined>(undefined)
  const [keyword, setKeyword] = useState('')
  const [zero, setZero] = useState<'all' | 'hide' | 'only'>('all')
  const [warningOnly, setWarningOnly] = useState(false)
  const [pagination, setPagination] = useState({ pageNum: 1, pageSize: 10 })

  const { data, isLoading, isFetching, isError } = useQuery({
    queryKey: ['inventory', 'stocks', { warehouseId, keyword, zero, warningOnly, ...pagination }],
    queryFn: () =>
      fetchStockPage({
        ...pagination,
        warehouseId,
        keyword: keyword || undefined,
        zero,
        warningOnly,
      }),
    placeholderData: keepPreviousData,
  })

  // 预警汇总：只取 total 数做顶部提示（当前筛选维度下）
  const { data: warnings } = useQuery({
    queryKey: ['inventory', 'warnings', 'summary', warehouseId],
    queryFn: () => fetchWarningPage({ pageNum: 1, pageSize: 1, warehouseId }),
    placeholderData: keepPreviousData,
  })

  // 仓库下拉（基础资料只读接口）
  const { data: warehouses } = useQuery({
    queryKey: ['inventory', 'warehouseOptions'],
    queryFn: fetchWarehouseOptions,
    staleTime: 5 * 60 * 1000,
  })
  const whOptions = (warehouses ?? []).map((w: WarehouseOption) => ({
    label: `${w.code} ${w.name}`,
    value: w.id,
  }))

  const warningTotal = warnings?.total ?? 0

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        即时库存
      </Title>

      {warningTotal > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message={`当前有 ${warningTotal} 条低于安全库存的记录`}
          action={
            <Button
              size="small"
              danger={warningOnly}
              onClick={() => setWarningOnly(!warningOnly)}
            >
              {warningOnly ? '取消预警过滤' : '仅看预警'}
            </Button>
          }
        />
      )}

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Select
            style={{ width: 220 }}
            placeholder="全部仓库"
            allowClear
            showSearch
            optionFilterProp="label"
            options={whOptions}
            value={warehouseId}
            onChange={(v) => {
              setWarehouseId(v)
              setPagination((p) => ({ ...p, pageNum: 1 }))
            }}
          />
          <Input.Search
            style={{ width: 240 }}
            placeholder="物料编码 / 名称"
            allowClear
            onSearch={(v) => {
              setKeyword(v)
              setPagination((p) => ({ ...p, pageNum: 1 }))
            }}
          />
          <Select
            style={{ width: 140 }}
            value={zero}
            onChange={(v) => {
              setZero(v)
              setPagination((p) => ({ ...p, pageNum: 1 }))
            }}
            options={[
              { label: '全部库存', value: 'all' },
              { label: '隐藏零库存', value: 'hide' },
              { label: '仅看零库存', value: 'only' },
            ]}
          />
        </Space>

        {isError ? (
          <Alert type="error" showIcon message="库存查询失败，请稍后重试" style={{ marginBottom: 12 }} />
        ) : null}

        <Table<StockRow>
          rowKey="id"
          columns={COLUMNS}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          locale={{ emptyText: '暂无库存记录' }}
          onRow={(record) =>
            record.belowSafety ? { style: { background: '#fff1f0' } } : {}
          }
          pagination={{
            total: data?.total ?? 0,
            current: data?.pageNum ?? 1,
            pageSize: data?.pageSize ?? 10,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (pageNum, pageSize) => setPagination({ pageNum, pageSize }),
          }}
        />
      </Card>
    </div>
  )
}
