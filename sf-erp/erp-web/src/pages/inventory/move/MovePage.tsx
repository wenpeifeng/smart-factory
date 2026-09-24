import { Card, Tabs, Typography } from 'antd'
import TransferPanel from '@/pages/inventory/move/TransferPanel'
import OtherDocPanel from '@/pages/inventory/move/OtherDocPanel'

const { Title } = Typography

/**
 * 仓库调拨 + 其他出入库页（Tab 布局）：
 * - 仓库调拨：即时执行，一出一入同事务；
 * - 其他入库/出库：草稿单据 → 审核过账。
 */
export default function MovePage() {
  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        仓库调拨
      </Title>
      <Card>
        <Tabs
          defaultActiveKey="transfer"
          items={[
            { key: 'transfer', label: '仓库调拨', children: <TransferPanel /> },
            { key: 'other-in', label: '其他入库单', children: <OtherDocPanel kind="in" /> },
            { key: 'other-out', label: '其他出库单', children: <OtherDocPanel kind="out" /> },
          ]}
        />
      </Card>
    </div>
  )
}
