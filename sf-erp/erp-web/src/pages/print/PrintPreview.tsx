import { Alert, Button, Space, Spin } from 'antd'
import { ArrowLeftOutlined, PrinterOutlined, ReloadOutlined } from '@ant-design/icons'
import { motion } from 'framer-motion'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getPrintData, type PrintBizType } from '@/api/print'
import { PRINT_TEMPLATES } from './templates'
import './printDoc.css'

/**
 * A4 打印预览页（/print/preview/:bizType/:id）。
 * <p>
 * 工具条（返回 / 打印 / 刷新）为非打印元素（.no-print + @media print 隐藏）；
 * 纸张区域只做入场渐显（0.25s，克制），打印物本身无任何动效；
 * 打印按钮直接 window.print()，由 printDoc.css 的 @media print 只输出纸张。
 */
export default function PrintPreview() {
  const navigate = useNavigate()
  const { bizType, id } = useParams()
  const [searchParams] = useSearchParams()
  const month = searchParams.get('month') ?? undefined

  const validType = bizType != null && bizType in PRINT_TEMPLATES

  const {
    data,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['print', 'data', bizType, id, month],
    queryFn: () => getPrintData(bizType as PrintBizType, Number(id), month),
    enabled: validType && Boolean(id),
  })

  const Template = validType ? PRINT_TEMPLATES[bizType as PrintBizType] : undefined

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* 工具条：非打印元素 */}
      <div className="no-print" style={{ padding: '12px 16px', flex: '0 0 auto' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/print')}>
            返回打印中心
          </Button>
          <Button icon={<ReloadOutlined />} onClick={() => refetch()}>
            刷新
          </Button>
          <Button type="primary" icon={<PrinterOutlined />} onClick={() => window.print()} disabled={!data}>
            打印
          </Button>
        </Space>
      </div>

      {/* 预览画布：灰底 + 纸张 */}
      <div className="print-canvas" style={{ flex: '1 1 auto', overflow: 'auto' }}>
        {isLoading && (
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: 320 }}>
            <Spin tip="打印数据加载中..." />
          </div>
        )}

        {!isLoading && isError && (
          <div style={{ maxWidth: 720, margin: '24px auto' }}>
            <Alert
              type="error"
              showIcon
              message="打印数据加载失败"
              description={(error as Error)?.message ?? '请稍后刷新重试'}
              action={
                <Button size="small" onClick={() => refetch()}>
                  重试
                </Button>
              }
            />
          </div>
        )}

        {!isLoading && !isError && data && Template && (
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.25, ease: 'easeOut' }}
          >
            <Template data={data} />
          </motion.div>
        )}
      </div>
    </div>
  )
}
