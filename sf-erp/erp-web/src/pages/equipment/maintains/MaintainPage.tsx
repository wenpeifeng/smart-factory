import { Tabs } from 'antd'
import RepairPanel from '@/pages/equipment/maintains/RepairPanel'
import PlanPanel from '@/pages/equipment/maintains/PlanPanel'

/**
 * 保养维修页：Tab 一 = 维修工单（报修流转/接单/完工含备件出库）；
 * Tab 二 = 保养计划（到期高亮 + 执行顺延周期）。
 * 路径 /equipment/maintains，与 MainLayout 菜单「保养维修」一致。
 */
export default function MaintainPage() {
  return (
    <div className="erp-page">
      <Tabs
        defaultActiveKey="repair"
        items={[
          { key: 'repair', label: '维修工单', children: <RepairPanel /> },
          { key: 'plan', label: '保养计划', children: <PlanPanel /> },
        ]}
      />
    </div>
  )
}
