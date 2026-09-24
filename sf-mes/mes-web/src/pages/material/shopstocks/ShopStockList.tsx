import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Input, Table } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import PageHeader from '@/components/PageHeader'
import { getShopStocks, type ShopStock } from '@/api/material'

/** 线边仓库存查询页 */
export default function ShopStockList() {
  const [keyword, setKeyword] = useState('')
  const { data, isLoading } = useQuery({
    queryKey: ['material', 'shopstocks', keyword],
    queryFn: () => getShopStocks({ pageNum: 1, pageSize: 20, keyword: keyword || undefined }),
  })

  const columns: ColumnsType<ShopStock> = [
    { title: '物料', render: (_, r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}` },
    { title: '批次', dataIndex: 'batchNo', render: (v) => v || '-' },
    { title: '库存数量', dataIndex: 'qty', align: 'right' },
  ]

  return (
    <>
      <PageHeader title="线边仓库存" subtitle="车间线边物料库存（领料入/退料出/齐套分析数据源）" />
      <Table<ShopStock>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        title={() => <Input.Search placeholder="物料编码/名称" allowClear style={{ width: 220 }} onSearch={setKeyword} />}
      />
    </>
  )
}
