import { useState } from 'react'
import { Alert, Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  DOC_CHAIN_TYPES,
  REVERSE_PERM,
  fetchDocChain,
  isReversible,
  reverseDoc,
  type DocChain,
  type DocChainNode,
} from '@/api/basedata'
import HasPerm from '@/components/HasPerm'

/**
 * 单据联查页（T7）：以任意单据为锚点展示上下游链路（报价→订单→出库→发票→
 * 应收→收款→凭证 / 采购：订单→入库→发票→应付→付款→凭证），
 * 链上"已审核且未红冲"的可红冲单据可直接发起通用红冲（生成负数反向单）。
 */

/** 数值状态标签（各单据 status 口径不同，这里只做常见值渲染） */
function statusTag(node: DocChainNode) {
  if (node.reversed) return <Tag color="red">已红冲</Tag>
  const s = node.status ?? ''
  if (s === '0') return <Tag>草稿</Tag>
  if (s === '10' || s === 'AUDITED' || s === 'POSTED') return <Tag color="green">已审核</Tag>
  if (s === '20') return <Tag color="orange">已红冲</Tag>
  if (s === '30' || s === '90' || s === 'CLOSED') return <Tag color="default">已关闭</Tag>
  if (s === 'SETTLED') return <Tag color="green">已结清</Tag>
  if (s === 'PARTIAL') return <Tag color="orange">部分核销</Tag>
  if (s === 'UNSETTLED') return <Tag color="blue">未核销</Tag>
  return <Tag>{s}</Tag>
}

export default function DocChainPage() {
  const [form] = Form.useForm<{ bizType: string; bizId: number }>()
  const [chain, setChain] = useState<DocChain | null>(null)
  const [loading, setLoading] = useState(false)
  const [reversing, setReversing] = useState<DocChainNode | null>(null)
  const [reason, setReason] = useState('')
  const [reverseLoading, setReverseLoading] = useState(false)

  const query = async (values: { bizType: string; bizId: number }) => {
    setLoading(true)
    try {
      const data = await fetchDocChain(values.bizType, values.bizId)
      setChain(data)
      if (!data.downstream.length && !data.upstream.length) {
        message.info('该单据无上下游关联单据')
      }
    } catch {
      setChain(null)
    } finally {
      setLoading(false)
    }
  }

  const doReverse = async () => {
    if (!reversing) return
    setReverseLoading(true)
    try {
      const result = await reverseDoc(reversing.docType, reversing.docId, reason)
      message.success(`红冲成功：已生成反向单 ${result.reverseDocNo}`)
      setReversing(null)
      setReason('')
      // 红冲后刷新链路（节点会出现红冲配对）
      const values = form.getFieldsValue()
      if (values.bizType && values.bizId) await query(values)
    } finally {
      setReverseLoading(false)
    }
  }

  const columns: ColumnsType<DocChainNode> = [
    { title: '单据类型', dataIndex: 'docName', width: 110 },
    { title: '单号', dataIndex: 'docNo', width: 200 },
    { title: '业务日期', dataIndex: 'bizDate', width: 110 },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 130,
      align: 'right',
      render: (v: number | null) =>
        v == null ? '-' : v.toLocaleString('zh-CN', { minimumFractionDigits: 2 }),
    },
    { title: '状态', dataIndex: 'status', width: 100, render: (_, node) => statusTag(node) },
    {
      title: '操作',
      key: 'op',
      width: 90,
      render: (_, node) => {
        const reversible =
          isReversible(node.docType) && !node.reversed && (node.status === '10' || node.status === 'AUDITED')
        if (!reversible) return null
        return (
          <HasPerm code={REVERSE_PERM[node.docType]}>
            <Popconfirm
              title={`确认红冲 ${node.docNo}？`}
              description="将生成负数反向单并回冲库存/应收应付"
              onConfirm={() => setReversing(node)}
              okText="红冲"
              okButtonProps={{ danger: true }}
              cancelText="取消"
            >
              <Button size="small" danger>
                红冲
              </Button>
            </Popconfirm>
          </HasPerm>
        )
      },
    },
  ]

  // 状态列渲染依赖节点级 reversed 标记（非纯 status 值）

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card title="单据联查">
        <Form
          form={form}
          layout="inline"
          initialValues={{ bizType: 'SAL_ORDER' }}
          onFinish={query}
        >
          <Form.Item name="bizType" rules={[{ required: true, message: '请选择单据类型' }]}>
            <Select
              options={DOC_CHAIN_TYPES}
              style={{ width: 160 }}
              placeholder="单据类型"
            />
          </Form.Item>
          <Form.Item name="bizId" rules={[{ required: true, message: '请输入单据 ID' }]}>
            <InputNumber min={1} precision={0} placeholder="单据 ID" style={{ width: 140 }} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading}>
              查询链路
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {chain && (
        <>
          <Card title={`锚点单据（${chain.anchor.docName ?? chain.anchor.docType}）`} size="small">
            <Space size="middle" wrap>
              <span>单号：{chain.anchor.docNo}</span>
              <span>业务日期：{chain.anchor.bizDate ?? '-'}</span>
              <span>
                金额：
                {chain.anchor.amount == null
                  ? '-'
                  : chain.anchor.amount.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}
              </span>
              {statusTag(chain.anchor)}
            </Space>
          </Card>

          <Card title={`上游单据（${chain.upstream.length}）`} size="small">
            <Table
              rowKey={(n) => `${n.docType}:${n.docId}`}
              size="small"
              columns={columns}
              dataSource={chain.upstream}
              pagination={false}
            />
          </Card>

          <Card title={`下游单据（${chain.downstream.length}）`} size="small">
            <Table
              rowKey={(n) => `${n.docType}:${n.docId}`}
              size="small"
              columns={columns}
              dataSource={chain.downstream}
              pagination={false}
            />
          </Card>

          <Alert
            type="info"
            showIcon
            message="红冲说明"
            description="红冲生成负数反向单（单号=原单号+R）并反向过账库存/应收应付；原单标记为已红冲。已核销的应收应付需先红冲对应收付款单。"
          />
        </>
      )}

      <Modal
        open={reversing != null}
        title={reversing ? `红冲确认：${reversing.docNo}` : ''}
        onOk={doReverse}
        onCancel={() => setReversing(null)}
        confirmLoading={reverseLoading}
        okText="确认红冲"
        okButtonProps={{ danger: true }}
        cancelText="取消"
      >
        <p>红冲将生成负数反向单并回冲库存与应收应付，操作不可撤销。</p>
        <Input.TextArea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="红冲原因（必填，审计留痕）"
          rows={3}
          maxLength={200}
          showCount
        />
      </Modal>
    </Space>
  )
}
