import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  Descriptions,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PrinterOutlined } from '@ant-design/icons'
import { usePrint } from '@/hooks/usePrint'
import {
  auditVoucher,
  deleteVoucher,
  getFinanceBookId,
  getVoucher,
  pageVouchers,
  setFinanceBookId,
  type VoucherDetailVO,
  type VoucherStatus,
  type VoucherVO,
} from '@/api/finance'
import BookSelect from '@/pages/finance/components/BookSelect'
import VoucherFormModal from '@/pages/finance/voucher/VoucherFormModal'

const { Title, Text } = Typography

/** 金额展示 */
const money = (v: number) => v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/**
 * 总账凭证页：
 * 凭证列表 + 录入/编辑（分录行增删、实时借贷平衡条、不平禁止提交）+ 审核过账 + 明细查看。
 * 过账（POSTED）后的凭证禁改禁删，与金蝶总账行为一致。
 */
export default function VoucherList() {
  const [bookId, setBookId] = useState<number>(getFinanceBookId())
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<VoucherStatus | undefined>()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<VoucherDetailVO | null>(null)
  const [detailId, setDetailId] = useState<number | null>(null)
  const queryClient = useQueryClient()
  const { openPrint } = usePrint()

  /** 页内切换账套：更新模块上下文（X-Book-Id）并失效全部财务缓存 */
  const switchBook = (id: number) => {
    setFinanceBookId(id)
    setBookId(id)
    setPageNum(1)
    queryClient.invalidateQueries({ queryKey: ['finance'] })
  }

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['finance', 'vouchers', bookId, { pageNum, pageSize, keyword, status }],
    queryFn: () => pageVouchers({ pageNum, pageSize, keyword: keyword || undefined, status }),
  })

  // 明细查看（按需加载；key 带账套防跨账套串缓存）
  const { data: detail, isLoading: detailLoading } = useQuery({
    queryKey: ['finance', 'voucher', bookId, detailId],
    queryFn: () => getVoucher(detailId as number),
    enabled: detailId != null,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['finance', 'vouchers'] })

  const auditMutation = useMutation({
    mutationFn: auditVoucher,
    onSuccess: () => {
      message.success('审核成功，凭证已过账')
      invalidate()
    },
  })
  const deleteMutation = useMutation({
    mutationFn: deleteVoucher,
    onSuccess: () => {
      message.success('凭证已删除')
      invalidate()
    },
  })

  const columns: ColumnsType<VoucherVO> = [
    { title: '凭证号', dataIndex: 'voucherNo', width: 150 },
    { title: '凭证日期', dataIndex: 'voucherDate', width: 110 },
    { title: '凭证字', dataIndex: 'word', width: 80, align: 'center' },
    { title: '借方合计', dataIndex: 'totalDebit', align: 'right', width: 120, render: money },
    { title: '贷方合计', dataIndex: 'totalCredit', align: 'right', width: 120, render: money },
    { title: '附件', dataIndex: 'attachmentCount', width: 70, align: 'right' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: VoucherStatus) =>
        v === 'POSTED' ? <Tag color="success">已过账</Tag> : <Tag color="default">草稿</Tag>,
    },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
      key: 'action',
      width: 230,
      render: (_, row) => (
        <Space size={0}>
          {row.status === 'DRAFT' ? (
            <>
              <HasPerm code="finance:voucher:edit">
                <Button
                  type="link"
                  size="small"
                  onClick={async () => {
                    // 编辑需先拉取明细回填分录，再打开弹窗
                    const d = await getVoucher(row.id)
                    setEditing(d)
                    setFormOpen(true)
                  }}
                >
                  编辑
                </Button>
              </HasPerm>
              <HasPerm code="finance:voucher:audit">
                <Button
                  type="link"
                  size="small"
                  loading={auditMutation.isPending && auditMutation.variables === row.id}
                  onClick={() => auditMutation.mutate(row.id)}
                >
                  审核
                </Button>
              </HasPerm>
              <HasPerm code="finance:voucher:delete">
              <Popconfirm title="确认删除该草稿凭证？" onConfirm={() => deleteMutation.mutate(row.id)}>
                <Button type="link" size="small" danger>
                  删除
                </Button>
              </Popconfirm>
              </HasPerm>
            </>
          ) : null}
          <Button type="link" size="small" onClick={() => setDetailId(row.id)}>
            明细
          </Button>
          {row.status === 'POSTED' && (
            <HasPerm code="finance:voucher:list">
              <Button
                type="link"
                size="small"
                icon={<PrinterOutlined />}
                onClick={() => openPrint('voucher', row.id)}
              >
                打印
              </Button>
            </HasPerm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        总账凭证
      </Title>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <span style={{ color: '#666' }}>账套：</span>
          <BookSelect value={bookId} onChange={switchBook} />
          <Input.Search
            placeholder="凭证号模糊搜索"
            style={{ width: 220 }}
            allowClear
            onSearch={(v) => {
              setKeyword(v)
              setPageNum(1)
            }}
          />
          <Select<VoucherStatus>
            style={{ width: 130 }}
            placeholder="凭证状态"
            allowClear
            value={status}
            onChange={(v) => {
              setStatus(v)
              setPageNum(1)
            }}
            options={[
              { value: 'DRAFT', label: '草稿' },
              { value: 'POSTED', label: '已过账' },
            ]}
          />
          <HasPerm code="finance:voucher:add">
            <Button
              type="primary"
              onClick={() => {
                setEditing(null)
                setFormOpen(true)
              }}
            >
              新增凭证
            </Button>
          </HasPerm>
        </Space>

        <Table<VoucherVO>
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

      <VoucherFormModal open={formOpen} initial={editing} onClose={() => setFormOpen(false)} />

      {/* 凭证明细（只读） */}
      <Modal
        open={detailId != null}
        title={`凭证明细 ${detail?.voucherNo ?? ''}`}
        footer={null}
        width={860}
        onCancel={() => setDetailId(null)}
      >
        {detail ? (
          <>
            <Descriptions size="small" column={3} bordered style={{ marginBottom: 12 }}>
              <Descriptions.Item label="凭证日期">{detail.voucherDate}</Descriptions.Item>
              <Descriptions.Item label="凭证字">{detail.word}</Descriptions.Item>
              <Descriptions.Item label="状态">
                {detail.status === 'POSTED' ? <Tag color="success">已过账</Tag> : <Tag>草稿</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label="借方合计">
                <Text strong>{money(detail.totalDebit)}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="贷方合计">
                <Text strong>{money(detail.totalCredit)}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="附件张数">{detail.attachmentCount}</Descriptions.Item>
            </Descriptions>
            <Table
              rowKey="lineNo"
              size="small"
              pagination={false}
              dataSource={detail.entries}
              columns={[
                { title: '行号', dataIndex: 'lineNo', width: 60 },
                { title: '摘要', dataIndex: 'summary' },
                {
                  title: '科目',
                  key: 'subject',
                  render: (_, e) => `${e.subjectCode ?? ''} ${e.subjectName ?? ''}`,
                },
                {
                  title: '借方',
                  dataIndex: 'debitAmount',
                  align: 'right',
                  width: 120,
                  render: (v: number) => (v > 0 ? money(v) : '-'),
                },
                {
                  title: '贷方',
                  dataIndex: 'creditAmount',
                  align: 'right',
                  width: 120,
                  render: (v: number) => (v > 0 ? money(v) : '-'),
                },
              ]}
            />
          </>
        ) : null}
        {detailLoading ? <Text type="secondary">加载中...</Text> : null}
      </Modal>
    </div>
  )
}
