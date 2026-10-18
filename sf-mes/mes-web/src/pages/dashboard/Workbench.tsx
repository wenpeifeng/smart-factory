import { Card, Col, Row, Statistic, Typography } from 'antd'
import {
  AuditOutlined,
  FileSyncOutlined,
  ScanOutlined,
  WarningOutlined,
} from '@ant-design/icons'

/**
 * 车间工作台 —— MES 首页（P1 骨架版）。
 *
 * 按 AGENTS 架构决策：MES 前端首页=车间工作台，提供
 * 待办工单 / 扫码报工入口 / 安灯呼叫 三块核心能力。
 * P1 仅静态骨架；P2 接入 execution 派工与过站报工接口后
 * 由静态卡位替换为真实数据（TanStack Query）。
 */
export default function Workbench() {
  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        车间工作台
      </Typography.Title>
      <Typography.Paragraph type="secondary">
        待办工单与实时过站数据将在 P2 执行模块上线后接入，当前为骨架预览。
      </Typography.Paragraph>

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={8}>
          <Card hoverable>
            <Statistic
              title="待执行工单"
              value={0}
              prefix={<FileSyncOutlined style={{ color: '#1668dc' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card hoverable>
            <Statistic
              title="今日过站报工"
              value={0}
              prefix={<AuditOutlined style={{ color: '#36cfc9' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card hoverable>
            <Statistic
              title="安灯呼叫（未响应）"
              value={0}
              prefix={<WarningOutlined style={{ color: '#faad14' }} />}
            />
          </Card>
        </Col>

        <Col span={24}>
          <Card
            title="快捷入口"
            extra={
              <a href="/m/login" target="_blank" rel="noreferrer">
                打开移动端门户
              </a>
            }
          >
            <Typography.Text type="secondary">
              <ScanOutlined /> 扫码报工请使用移动端门户（/m）或车间终端；桌面端派工与工单追踪在
              P2 上线。
            </Typography.Text>
          </Card>
        </Col>
      </Row>
    </div>
  )
}
