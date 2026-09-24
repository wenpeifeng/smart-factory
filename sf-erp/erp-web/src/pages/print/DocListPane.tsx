import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, DatePicker, Input, Tag } from 'antd'
import { PrinterOutlined, SearchOutlined } from '@ant-design/icons'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import PageTable from '@/components/PageTable'
import { useDebounce } from '@/hooks/useDebounce'
import { BIZ_TYPE_META, type PrintBizType, type PrintDocRow } from '@/api/print'
import { fmtMoney } from '@/pages/report/shared'

/**
 * 打印中心的单据选择列表（每个 Tab 一个）。
 * <p>
 * 统一列：单据编号 / 往来单位 / 日期 / 金额 / 状态 + 操作（预览打印）；
 * 对账单 Tab 额外提供月份选择（默认当月），id 为客户 ID。
 * 点击「预览打印」进入 A4 预览页。
 */

interface DocListPaneProps {
  bizType: PrintBizType
}

export default function DocListPane({ bizType }: DocListPaneProps) {
  const navigate = useNavigate()
  const meta = BIZ_TYPE_META[bizType]

  const [keyword, setKeyword] = useState('')
  const debouncedKw = useDebounce(keyword)
  // 对账单月份（仅 statement 使用；切换月份触发重新查询）
  const [month, setMonth] = useState<Dayjs>(dayjs())

  const isStatement = bizType === 'statement'
  const monthParam = isStatement ? month.format('YYYY-MM') : undefined

  const columns: ColumnsType<PrintDocRow> = [
    { title: isStatement ? '客户编码' : '单据编号', dataIndex: 'billNo', width: 150 },
    { title: meta.partyLabel, dataIndex: 'partyName', width: 170, ellipsis: true },
    { title: meta.dateLabel, dataIndex: 'billDate', width: 110 },
    {
      title: meta.amountLabel,
      dataIndex: 'amount',
      width: 120,
      align: 'right',
      render: (v: number) => fmtMoney(v),
    },
    {
      title: '状态',
      dataIndex: 'statusLabel',
      width: 90,
      render: (v: string | null) => (v ? <Tag>{v}</Tag> : '-'),
    },
    ...(isStatement
      ? ([
          {
            title: '当月出库',
            dataIndex: 'extra',
            width: 110,
            render: (v: string | null) => v ?? '-',
          },
        ] as ColumnsType<PrintDocRow>)
      : []),
    {
      title: '操作',
      key: 'action',
      width: 110,
      fixed: 'right',
      render: (_, row) => (
        <Button
          type="link"
          size="small"
          icon={<PrinterOutlined />}
          onClick={() =>
            navigate(`/print/preview/${bizType}/${row.id}${monthParam ? `?month=${monthParam}` : ''}`)
          }
        >
          预览打印
        </Button>
      ),
    },
  ]

  return (
    <PageTable<PrintDocRow>
      url={`/report/print/list/${bizType}`}
      queryKey={['print', 'docs', bizType, debouncedKw, monthParam ?? '']}
      params={{ keyword: debouncedKw || undefined, month: monthParam }}
      columns={columns}
      toolbar={
        <>
          <Input
            allowClear
            style={{ width: 240 }}
            prefix={<SearchOutlined style={{ color: '#8c939c' }} />}
            placeholder={isStatement ? '搜索客户编码' : '搜索单号 / 往来单位'}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          {isStatement && (
            <DatePicker
              picker="month"
              allowClear={false}
              value={month}
              onChange={(v) => setMonth(v ?? dayjs())}
            />
          )}
        </>
      }
    />
  )
}
