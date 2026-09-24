import { useState } from 'react'
import { Tabs } from 'antd'
import PageHeader from '@/components/PageHeader'
import { BIZ_TYPE_META, type PrintBizType } from '@/api/print'
import DocListPane from './DocListPane'

/**
 * 单据打印中心：5 类单据 Tab（销售订单/送货单/采购订单/月度对账单/记账凭证）。
 * 每个 Tab = 单据选择列表（编号/往来单位/日期/金额）→ 点击进入 A4 打印预览。
 * 数据来自报表中心只读聚合接口 /report/print/*。
 */
export default function PrintCenter() {
  const [activeTab, setActiveTab] = useState<PrintBizType>('sal_order')

  const items = (Object.keys(BIZ_TYPE_META) as PrintBizType[]).map((type) => ({
    key: type,
    label: BIZ_TYPE_META[type].label,
    children: type === activeTab ? <DocListPane bizType={type} /> : null,
  }))

  return (
    <div className="erp-page">
      <PageHeader
        title="打印中心"
        subtitle="销售订单 / 送货单 / 采购订单 / 月度对账单 / 记账凭证的 A4 打印预览与打印"
      />
      <Tabs activeKey={activeTab} onChange={(k) => setActiveTab(k as PrintBizType)} items={items} destroyOnHidden />
    </div>
  )
}
