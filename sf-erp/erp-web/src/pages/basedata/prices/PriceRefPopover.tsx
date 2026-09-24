import { Popover, Space, Spin, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { FieldNumberOutlined } from '@ant-design/icons'
import {
  getPriceHistory,
  getPriceRef,
  getPricingQuote,
} from '@/api/basedata'

const { Text } = Typography

/** 单价显示：最多 4 位小数（与后端 DECIMAL(18,4) 口径一致） */
function fmtPrice(v: number | null | undefined): string {
  if (v == null) return '-'
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
}

/**
 * 录单「价格参照」Popover（V131 价格中心）。
 *
 * - purchase 模式（采购订单行）：合同价（优先，标注"合同"）+ 最近成交价 + 历史 3 条；
 * - sales 模式（销售订单/报价单行）：定价引擎命中策略 + 参照价列表
 *   （特价 > 等级价 > 批量价 > 成本加成）。
 *
 * 只读参照，不影响所在表单的取值与提交逻辑；物料未选时显示禁用态。
 */
export default function PriceRefPopover({
  mode,
  supplierId,
  customerId,
  materialId,
  qty,
}: {
  mode: 'purchase' | 'sales'
  /** 采购模式：供应商（表头已选供应商联动） */
  supplierId?: number
  /** 销售模式：客户（等级参与策略匹配） */
  customerId?: number
  /** 当前行物料 */
  materialId?: number
  /** 当前行数量（批量价阶梯匹配） */
  qty?: number
}) {
  const purchaseReady = mode === 'purchase' && materialId != null && supplierId != null
  const salesReady = mode === 'sales' && materialId != null && customerId != null

  // 采购参照：合同价 + 最近价
  const refQuery = useQuery({
    queryKey: ['price-ref', supplierId, materialId],
    queryFn: () => getPriceRef(supplierId as number, materialId as number),
    enabled: purchaseReady,
    staleTime: 30_000,
  })
  // 采购参照：历史 3 条
  const historyQuery = useQuery({
    queryKey: ['price-history', materialId, supplierId],
    queryFn: () => getPriceHistory(materialId as number, supplierId, 3),
    enabled: purchaseReady,
    staleTime: 30_000,
  })
  // 销售参照：定价引擎
  const quoteQuery = useQuery({
    queryKey: ['pricing-quote', customerId, materialId, qty ?? 0],
    queryFn: () => getPricingQuote(customerId as number, materialId as number, qty),
    enabled: salesReady,
    staleTime: 30_000,
  })

  const loading =
    (purchaseReady && (refQuery.isLoading || historyQuery.isLoading)) ||
    (salesReady && quoteQuery.isLoading)

  const content = (
    <div style={{ width: 320 }}>
      {loading ? (
        <div style={{ textAlign: 'center', padding: 16 }}>
          <Spin size="small" />
        </div>
      ) : mode === 'purchase' ? (
        <PurchaseRefBlock
          contract={refQuery.data?.contract ?? null}
          last={refQuery.data?.last ?? null}
          history={historyQuery.data?.records ?? []}
        />
      ) : (
        <SalesRefBlock quote={quoteQuery.data ?? null} />
      )}
    </div>
  )

  const disabled = mode === 'purchase' ? !purchaseReady : !salesReady

  return (
    <Popover content={content} title="价格参照" trigger="click" placement="left" arrow={false}>
      {disabled ? (
        <Text type="secondary" style={{ fontSize: 12, cursor: 'not-allowed' }}>
          <FieldNumberOutlined /> 价格参照
        </Text>
      ) : (
        <a style={{ fontSize: 12 }}>
          <FieldNumberOutlined /> 价格参照
        </a>
      )}
    </Popover>
  )
}

/** 采购参照块：合同价（优先）+ 最近价 + 历史 3 条 */
function PurchaseRefBlock({
  contract,
  last,
  history,
}: {
  contract: {
    id: number
    price: number
    effectiveFrom: string
    effectiveTo: string
    remark: string | null
  } | null
  last: {
    id: number
    price: number
    sourceType: string
    sourceTypeLabel: string
    sourceNo: string
    priceDate: string
  } | null
  history: {
    id: number
    price: number
    sourceTypeLabel: string
    sourceNo: string
    priceDate: string
  }[]
}) {
  if (!contract && !last && history.length === 0) {
    return <Text type="secondary">该供应商+物料暂无成交记录/合同价</Text>
  }
  return (
    <Space direction="vertical" size={6} style={{ width: '100%' }}>
      {contract && (
        <div>
          <Tag color="gold">合同</Tag>
          <Text strong style={{ color: '#1668dc' }}>
            {fmtPrice(contract.price)}
          </Text>
          <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
            {contract.effectiveFrom} ~ {contract.effectiveTo}
            {contract.remark ? `（${contract.remark}）` : ''}
          </Text>
        </div>
      )}
      {last && (
        <div>
          <Tag color="blue">最近价</Tag>
          <Text strong>{fmtPrice(last.price)}</Text>
          <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
            {last.priceDate} {last.sourceTypeLabel} {last.sourceNo}
          </Text>
        </div>
      )}
      {history.length > 0 && (
        <div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            历史 {history.length} 条：
          </Text>
          {history.map((h) => (
            <div key={h.id} style={{ fontSize: 12, color: '#666', paddingLeft: 4 }}>
              {h.priceDate} · {fmtPrice(h.price)} · {h.sourceTypeLabel} {h.sourceNo}
            </div>
          ))}
        </div>
      )}
    </Space>
  )
}

/** 销售参照块：命中策略 + 参照价列表 */
function SalesRefBlock({
  quote,
}: {
  quote: {
    customerLevel: string
    unitPrice: number | null
    hitStrategy: string
    hitStrategyLabel: string
    hitReason: string
    references: { strategy: string; label: string; price: number; hit: boolean }[]
  } | null
}) {
  if (!quote) {
    return <Text type="secondary">请先选择客户与物料</Text>
  }
  const hitColor =
    quote.hitStrategy === 'SPECIAL'
      ? 'volcano'
      : quote.hitStrategy === 'LEVEL'
        ? 'gold'
        : quote.hitStrategy === 'QTY'
          ? 'cyan'
          : quote.hitStrategy === 'MARKUP'
            ? 'green'
            : 'default'
  return (
    <Space direction="vertical" size={6} style={{ width: '100%' }}>
      <div>
        <Tag color={hitColor}>{quote.hitStrategyLabel}</Tag>
        {quote.unitPrice != null ? (
          <Text strong style={{ color: '#1668dc', fontSize: 16 }}>
            {fmtPrice(quote.unitPrice)}
          </Text>
        ) : (
          <Text type="secondary">无可用价</Text>
        )}
        <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
          客户等级 {quote.customerLevel}
        </Text>
      </div>
      <div style={{ fontSize: 12, color: '#666' }}>{quote.hitReason}</div>
      {quote.references.length > 0 && (
        <div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            参照价（特价 &gt; 等级价 &gt; 批量价 &gt; 成本加成）：
          </Text>
          {quote.references.map((ref) => (
            <div key={ref.strategy} style={{ fontSize: 12, paddingLeft: 4 }}>
              {ref.hit ? <Text strong style={{ color: '#1668dc' }}>▶ </Text> : '　'}
              <Text style={{ color: ref.hit ? '#1668dc' : '#666' }}>
                {ref.label}：{fmtPrice(ref.price)}
              </Text>
            </div>
          ))}
        </div>
      )}
    </Space>
  )
}
