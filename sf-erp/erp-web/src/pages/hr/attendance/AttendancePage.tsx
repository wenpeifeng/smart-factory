import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  Upload,
} from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  attendanceApi,
  supportApi,
  type AttendanceImportResult,
  type AttendanceRow,
  type AttendanceStatus,
  type AttendanceSummaryRow,
} from '@/api/hr'

const { Title, Text } = Typography

const ATT_STATUS: Record<AttendanceStatus, { label: string; color: string }> = {
  NORMAL: { label: '正常', color: 'success' },
  ABSENT: { label: '缺勤', color: 'error' },
  LATE: { label: '迟到', color: 'warning' },
  LEAVE: { label: '请假', color: 'processing' },
  OVERTIME: { label: '加班', color: 'geekblue' },
}

/**
 * 考勤页：明细查询（员工/日期区间）、月度汇总（按员工统计各状态天数）、CSV 批量导入。
 */
export default function AttendancePage() {
  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        考勤管理
      </Title>
      <Tabs
        items={[
          { key: 'records', label: '考勤明细', children: <RecordsTab /> },
          { key: 'summary', label: '月度汇总', children: <SummaryTab /> },
          { key: 'import', label: 'CSV 导入', children: <ImportTab /> },
        ]}
      />
    </div>
  )
}

// ==================== 考勤明细 Tab ====================

function RecordsTab() {
  const [employeeId, setEmployeeId] = useState<number | undefined>()
  const [range, setRange] = useState<[dayjs.Dayjs, dayjs.Dayjs] | null>(null)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const { data: employees } = useQuery({
    queryKey: ['hr', 'employee-options'],
    queryFn: () => supportApi.employees(),
  })

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['hr', 'attendances', employeeId, range?.[0]?.format('YYYY-MM-DD'), range?.[1]?.format('YYYY-MM-DD'), pageNum, pageSize],
    queryFn: () =>
      attendanceApi.page({
        pageNum,
        pageSize,
        employeeId,
        from: range?.[0]?.format('YYYY-MM-DD'),
        to: range?.[1]?.format('YYYY-MM-DD'),
      }),
  })

  const columns: ColumnsType<AttendanceRow> = [
    { title: '考勤日期', dataIndex: 'attDate', width: 110 },
    { title: '工号', dataIndex: 'empNo', width: 150 },
    { title: '姓名', dataIndex: 'employeeName', width: 100 },
    { title: '部门', dataIndex: 'deptName', width: 110, render: (v) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: AttendanceStatus) => <Tag color={ATT_STATUS[v].color}>{ATT_STATUS[v].label}</Tag>,
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v) => v ?? '-' },
    { title: '更新时间', dataIndex: 'updateTime', width: 170 },
  ]

  return (
    <Card>
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          placeholder="员工筛选"
          style={{ width: 220 }}
          allowClear
          showSearch
          optionFilterProp="label"
          options={(employees ?? []).map((e) => ({
            label: `${e.empNo} ${e.name}`,
            value: e.id,
          }))}
          value={employeeId}
          onChange={(v) => {
            setPageNum(1)
            setEmployeeId(v)
          }}
        />
        <DatePicker.RangePicker
          value={range}
          onChange={(v) => {
            setPageNum(1)
            setRange(v as [dayjs.Dayjs, dayjs.Dayjs] | null)
          }}
        />
      </Space>
      <Table<AttendanceRow>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading || isFetching}
        pagination={{
          total: data?.total ?? 0,
          current: data?.pageNum ?? pageNum,
          pageSize: data?.pageSize ?? pageSize,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPageNum(p)
            setPageSize(s)
          },
        }}
      />
    </Card>
  )
}

// ==================== 月度汇总 Tab ====================

function SummaryTab() {
  const [month, setMonth] = useState<dayjs.Dayjs>(dayjs())

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['hr', 'attendance-summary', month.format('YYYY-MM')],
    queryFn: () => attendanceApi.summary(month.format('YYYY-MM')),
  })

  const columns: ColumnsType<AttendanceSummaryRow> = [
    { title: '工号', dataIndex: 'empNo', width: 150 },
    { title: '姓名', dataIndex: 'employeeName', width: 100 },
    { title: '部门', dataIndex: 'deptName', width: 110, render: (v) => v ?? '-' },
    { title: '正常（天）', dataIndex: 'normalDays', width: 100, align: 'right' },
    { title: '缺勤（天）', dataIndex: 'absentDays', width: 100, align: 'right' },
    { title: '迟到（天）', dataIndex: 'lateDays', width: 100, align: 'right' },
    { title: '请假（天）', dataIndex: 'leaveDays', width: 100, align: 'right' },
    { title: '加班（天）', dataIndex: 'overtimeDays', width: 100, align: 'right' },
    { title: '出勤合计（天）', dataIndex: 'attendedDays', width: 120, align: 'right' },
  ]

  return (
    <Card>
      <Space style={{ marginBottom: 16 }}>
        <DatePicker
          picker="month"
          value={month}
          onChange={(v) => v && setMonth(v)}
          allowClear={false}
        />
        <Text type="secondary">出勤合计 = 正常 + 迟到（到岗即出勤）</Text>
      </Space>
      <Table<AttendanceSummaryRow>
        rowKey="employeeId"
        columns={columns}
        dataSource={data ?? []}
        loading={isLoading || isFetching}
        pagination={false}
        locale={{ emptyText: '该月无考勤记录，请先在"CSV 导入"页导入' }}
      />
    </Card>
  )
}

// ==================== CSV 导入 Tab ====================

function ImportTab() {
  const queryClient = useQueryClient()
  const [result, setResult] = useState<AttendanceImportResult | null>(null)

  const importMutation = useMutation({
    mutationFn: (file: File) => attendanceApi.importCsv(file),
    onSuccess: (res) => {
      setResult(res)
      queryClient.invalidateQueries({ queryKey: ['hr', 'attendances'] })
      queryClient.invalidateQueries({ queryKey: ['hr', 'attendance-summary'] })
    },
  })

  return (
    <Card>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="CSV 格式：每行三列 工号,日期,状态"
        description={
          <div>
            <div>状态取值：NORMAL 正常 / ABSENT 缺勤 / LATE 迟到 / LEAVE 请假 / OVERTIME 加班（第四列备注可选）</div>
            <Text code>EMP202609050001,2026-08-01,NORMAL</Text>
            <div style={{ marginTop: 4 }}>
              同一员工同一日期重复导入时后值覆盖前值，适合按月整月重复导入（文件编码 UTF-8，首行表头可省略）。
            </div>
          </div>
        }
      />
      <Upload.Dragger
        accept=".csv"
        maxCount={1}
        showUploadList={false}
        customRequest={({ file, onSuccess: onDone, onError }) => {
          importMutation.mutate(file as File, {
            onSuccess: () => onDone?.({}),
            onError: (e) => onError?.(e as Error),
          })
        }}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">点击或拖拽 CSV 文件到此处导入考勤</p>
        <p className="ant-upload-hint">导入完成后显示逐行校验结果，失败行不影响成功行</p>
      </Upload.Dragger>

      {importMutation.isError && (
        <Alert type="error" showIcon style={{ marginTop: 16 }} message="导入失败，请检查文件格式" />
      )}

      {result && (
        <Alert
          style={{ marginTop: 16 }}
          type={result.failed > 0 ? 'warning' : 'success'}
          showIcon
          message={`导入完成：共 ${result.total} 行，成功 ${result.success} 行，失败 ${result.failed} 行`}
          description={
            result.errors.length > 0 ? (
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                {result.errors.map((e) => (
                  <li key={e}>{e}</li>
                ))}
              </ul>
            ) : undefined
          }
        />
      )}

      <div style={{ marginTop: 16 }}>
        <Button
          onClick={() => setResult(null)}
          disabled={!result}
        >
          清除结果
        </Button>
      </div>
    </Card>
  )
}
