import { useState } from 'react'
import { Alert, Button, Card, DatePicker, Descriptions, Space, Table, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { downloadReportCsv, getExpenseAnalysis, type ExpenseRow } from '@/api/report'
import { fmtMoney, fmtRatio } from '@/pages/report/shared'

/**
 * 费用分析：部门 × 科目 借方发生额（POSTED 凭证，损益+成本类借方科目）。
 */
export default function ExpensePane() {
  const [period, setPeriod] = useState<string>(dayjs().format('YYYY-MM'))

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['report', 'finance-expense', period],
    queryFn: () => getExpenseAnalysis(period),
  })

  const columns: ColumnsType<ExpenseRow> = [
    { title: '部门', dataIndex: 'deptName', key: 'deptName', width: 140,
      onFilter: (value, record) => record.deptName === value,
      filters: Array.from(new Set((data?.rows ?? []).map((r) => r.deptName))).map((d) => ({ text: d, value: d })) },
    { title: '科目编码', dataIndex: 'subjectCode', key: 'subjectCode', width: 100 },
    { title: '科目名称', dataIndex: 'subjectName', key: 'subjectName', ellipsis: true },
    { title: '借方发生额(元)', dataIndex: 'amount', key: 'amount', align: 'right', width: 140, render: (v: number) => fmtMoney(v) },
    { title: '占比', dataIndex: 'ratio', key: 'ratio', align: 'right', width: 100, render: (v: number) => fmtRatio(v) },
  ]

  const rows = data?.rows ?? []
  const deptTotal = rows.reduce<{ name: string; amount: number }[]>((acc, r) => {
    const hit = acc.find((x) => x.name === r.deptName)
    if (hit) hit.amount += r.amount
    else acc.push({ name: r.deptName, amount: r.amount })
    return acc
  }, [])

  return (
    <Card
      title={`费用分析 · ${data?.period ?? period}`}
      extra={
        <Space>
          <DatePicker
            picker="month"
            value={period ? dayjs(period) : null}
            onChange={(d) => setPeriod(d?.format('YYYY-MM') ?? dayjs().format('YYYY-MM'))}
            allowClear={false}
          />
          <Button
            icon={<DownloadOutlined />}
            onClick={() => downloadReportCsv('/report/finance/expense', { period }, `finance_expense_${period}.csv`)}
          >
            导出 CSV
          </Button>
        </Space>
      }
    >
      {isError && (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} message={`查询失败：${(error as Error)?.message ?? ''}`} />
      )}

      {deptTotal.length > 0 && (
        <Descriptions size="small" column={Math.min(deptTotal.length + 1, 4)} bordered style={{ marginBottom: 16 }}>
          <Descriptions.Item label="费用合计(元)">{fmtMoney(data?.totalAmount ?? 0)}</Descriptions.Item>
          {deptTotal.map((d) => (
            <Descriptions.Item key={d.name} label={`${d.name}(元)`}>{fmtMoney(d.amount)}</Descriptions.Item>
          ))}
        </Descriptions>
      )}

      <Table<ExpenseRow>
        rowKey={(r) => `${r.deptId}-${r.subjectCode}`}
        size="middle"
        loading={isLoading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 20, showSizeChanger: false, showTotal: (t) => `共 ${t} 行` }}
      />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        口径：已过账（POSTED）凭证分录，科目 = 损益类 + 成本类借方（5001/5101/6401/6601/6602/6603 等）；
        部门 = 凭证制单人所属部门（凭证无部门列，无部门用户归「未分配部门」）。
      </Typography.Text>
    </Card>
  )
}
