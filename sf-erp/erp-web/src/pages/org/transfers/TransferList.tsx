import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, DatePicker, Input, Popconfirm, Select, Space, Table } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import PageTable from '@/components/PageTable'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import { useDebounce } from '@/hooks/useDebounce'
import { useSubmitting } from '@/hooks/useSubmitting'
import {
  auditTransfer,
  cancelTransfer,
  getTransfer,
  listEnabledFactories,
  transferLineStats,
  type TransferDetail,
  type TransferLineStat,
  type TransferOrder,
} from '@/api/org'
import { fmtMoney, fmtQty } from '@/api/org'
import TransferEditModal from './TransferEditModal'
import TransferDetailModal from './TransferDetailModal'

const STATUS_OPTIONS = [
  { label: '草稿', value: 0 },
  { label: '已审核', value: 1 },
  { label: '已取消', value: 2 },
]

/** 期间线路汇总小表列 */
const statColumns: ColumnsType<TransferLineStat> = [
  {
    title: '调拨线路',
    key: 'line',
    render: (_, r) => `${r.fromFactoryName ?? '-'} → ${r.toFactoryName ?? '-'}`,
  },
  { title: '单数', dataIndex: 'transferCount', width: 80, align: 'right' },
  {
    title: '数量合计',
    dataIndex: 'totalQty',
    width: 120,
    align: 'right',
    render: (v) => fmtQty(v),
  },
  {
    title: '结算金额合计',
    dataIndex: 'totalAmount',
    width: 140,
    align: 'right',
    render: (v) => fmtMoney(v),
  },
]

/**
 * 组织间调拨页：调拨单分页 + 线路金额汇总卡 + 新建/编辑/审核(Popconfirm)/取消。
 * 审核即过账：调出仓扣库存、调入仓加库存、写 ORG_OUT/ORG_IN 流水，库存不足整单回滚。
 */
export default function TransferList() {
  const queryClient = useQueryClient()

  const [status, setStatus] = useState<number | undefined>()
  const [fromFactoryId, setFromFactoryId] = useState<number | undefined>()
  const [toFactoryId, setToFactoryId] = useState<number | undefined>()
  const [keyword, setKeyword] = useState('')
  const debouncedKeyword = useDebounce(keyword)

  // 线路汇总期间（默认本月）
  const [range, setRange] = useState<[Dayjs, Dayjs]>([
    dayjs().startOf('month'),
    dayjs(),
  ])

  // 弹窗态
  const [editOpen, setEditOpen] = useState(false)
  const [editing, setEditing] = useState<TransferDetail | null>(null)
  const [detail, setDetail] = useState<TransferDetail | null>(null)

  const { data: factoryOptions } = useQuery({
    queryKey: ['org', 'factories', 'enabled'],
    queryFn: listEnabledFactories,
  })
  const { data: stats, isFetching: statsLoading } = useQuery({
    queryKey: ['org', 'statistics', 'transfer', range[0].format('YYYY-MM-DD'), range[1].format('YYYY-MM-DD')],
    queryFn: () => transferLineStats(range[0].format('YYYY-MM-DD'), range[1].format('YYYY-MM-DD')),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['org'] })

  const auditSubmit = useSubmitting((id: number) => auditTransfer(id), {
    successText: '审核完成，库存已过账',
    onSuccess: invalidate,
  })

  const cancelSubmit = useSubmitting((id: number) => cancelTransfer(id), {
    successText: '已取消',
    onSuccess: invalidate,
  })

  const openEdit = async (row: TransferOrder | null) => {
    if (row) {
      const d = await getTransfer(row.id)
      setEditing(d)
    } else {
      setEditing(null)
    }
    setEditOpen(true)
  }

  const columns: ColumnsType<TransferOrder> = [
    { title: 'ID', dataIndex: 'id', width: 64 },
    { title: '调拨单号', dataIndex: 'transferNo', width: 150 },
    {
      title: '调出（工厂/仓库）',
      key: 'from',
      width: 180,
      render: (_, r) => `${r.fromFactoryName ?? '-'} / ${r.fromWarehouseName ?? '-'}`,
    },
    {
      title: '调入（工厂/仓库）',
      key: 'to',
      width: 180,
      render: (_, r) => `${r.toFactoryName ?? '-'} / ${r.toWarehouseName ?? '-'}`,
    },
    {
      title: '数量合计',
      dataIndex: 'totalQty',
      width: 100,
      align: 'right',
      render: (v) => fmtQty(v),
    },
    {
      title: '结算金额',
      dataIndex: 'totalAmount',
      width: 120,
      align: 'right',
      render: (v) => fmtMoney(v),
    },
    {
      title: '状态',
      dataIndex: 'statusText',
      width: 90,
      render: (v) => <StatusTag value={v} />,
    },
    { title: '审核时间', dataIndex: 'auditTime', width: 165, render: (v) => v ?? '-' },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v) => v ?? '-' },
    {
      title: '操作',
      key: 'action',
      width: 220,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => getTransfer(record.id).then(setDetail)}>
            详情
          </Button>
          {record.status === 0 && (
            <>
              <HasPerm code="org:transfer:edit">
                <Button type="link" size="small" onClick={() => openEdit(record)}>
                  编辑
                </Button>
              </HasPerm>
              <HasPerm code="org:transfer:audit">
                <Popconfirm
                  title="确认审核过账？"
                  description="调出仓将扣减库存、调入仓增加库存并写入调拨流水，不可撤销"
                  onConfirm={() => auditSubmit.run(record.id)}
                >
                  <Button type="link" size="small">
                    审核
                  </Button>
                </Popconfirm>
              </HasPerm>
              <HasPerm code="org:transfer:audit">
                <Popconfirm title="确认取消该草稿单？" onConfirm={() => cancelSubmit.run(record.id)}>
                  <Button type="link" size="small" danger>
                    取消
                  </Button>
                </Popconfirm>
              </HasPerm>
            </>
          )}
        </Space>
      ),
    },
  ]

  const factorySelectOptions = (factoryOptions ?? []).map((f) => ({
    label: `${f.code} ${f.name}`,
    value: f.id,
  }))

  return (
    <div className="erp-page">
      <PageHeader
        title="组织间调拨"
        subtitle="工厂间实物调拨：按价目自动计算内部结算价，审核即过账（调出仓扣减/调入仓增加）"
        extra={
          <HasPerm code="org:transfer:add">
            <Button type="primary" onClick={() => openEdit(null)}>
              新建调拨单
            </Button>
          </HasPerm>
        }
      />

      <Card title="调拨线路汇总（已审核）" size="small" style={{ marginBottom: 16 }}>
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <DatePicker.RangePicker
            value={range}
            onChange={(v) => {
              if (v && v[0] && v[1]) setRange([v[0], v[1]])
            }}
            allowClear={false}
          />
          <Table<TransferLineStat>
            rowKey={(r) => `${r.fromFactoryId}-${r.toFactoryId}`}
            size="small"
            columns={statColumns}
            dataSource={stats ?? []}
            loading={statsLoading}
            pagination={false}
            locale={{ emptyText: '所选期间暂无已审核调拨' }}
          />
        </Space>
      </Card>

      <PageTable<TransferOrder>
        url="/org/transfers"
        queryKey={['org', 'transfers', debouncedKeyword, status, fromFactoryId, toFactoryId]}
        params={{
          keyword: debouncedKeyword || undefined,
          status,
          fromFactoryId,
          toFactoryId,
        }}
        columns={columns}
        toolbar={
          <>
            <Input.Search
              placeholder="单号 / 工厂名搜索"
              style={{ width: 200 }}
              allowClear
              onSearch={(v) => {
                setKeyword(v)
              }}
            />
            <Select
              placeholder="状态"
              style={{ width: 110 }}
              allowClear
              options={STATUS_OPTIONS}
              value={status}
              onChange={setStatus}
            />
            <Select
              placeholder="调出工厂"
              style={{ width: 170 }}
              allowClear
              showSearch
              optionFilterProp="label"
              options={factorySelectOptions}
              value={fromFactoryId}
              onChange={setFromFactoryId}
            />
            <Select
              placeholder="调入工厂"
              style={{ width: 170 }}
              allowClear
              showSearch
              optionFilterProp="label"
              options={factorySelectOptions}
              value={toFactoryId}
              onChange={setToFactoryId}
            />
          </>
        }
      />

      <TransferEditModal
        open={editOpen}
        editing={editing}
        onClose={() => setEditOpen(false)}
        onSuccess={invalidate}
      />
      <TransferDetailModal detail={detail} onClose={() => setDetail(null)} />
    </div>
  )
}
