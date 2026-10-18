import React, { useEffect } from 'react'
import ReactDOM from 'react-dom/client'
import { ConfigProvider, App as AntdApp } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import enUS from 'antd/locale/en_US'
import type { Locale } from 'antd/lib/locale'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import 'dayjs/locale/en'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { router } from '@/router'
import '@/locales' // i18next 初始化（语言持久化见 src/locales/index.ts）
import '@/styles/global.css'

/**
 * React Query 全局配置：
 * - 窗口聚焦不自动重新请求（ERP 表单页防止填一半被刷新数据）；
 * - 失败不自动重试（错误交给页面自行处理展示）。
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { refetchOnWindowFocus: false, retry: 1 },
  },
})

/**
 * 语言联动层：antd 组件文案 / dayjs 格式随 i18next 语言即时切换。
 * useTranslation 订阅语言变化，语言一换整棵树重渲染。
 */
function App() {
  const { i18n } = useTranslation()
  const isZh = i18n.language.startsWith('zh')
  const antdLocale: Locale = isZh ? zhCN : enUS

  useEffect(() => {
    dayjs.locale(isZh ? 'zh-cn' : 'en')
  }, [isZh])

  return (
    <ConfigProvider
      locale={antdLocale}
      theme={{
        token: {
          colorPrimary: '#14785d',
          colorInfo: '#14785d',
          colorSuccess: '#238467',
          colorWarning: '#c9821c',
          colorBgLayout: '#f4f7f6',
          colorBgContainer: '#ffffff',
          colorText: '#172b26',
          colorTextSecondary: '#526760',
          colorTextTertiary: '#778983',
          colorBorder: '#cfdbd6',
          colorBorderSecondary: '#e2e9e6',
          controlOutline: 'rgba(20, 120, 93, 0.18)',
          borderRadius: 8,
          fontSize: 14,
          lineWidth: 1,
        },
        components: {
          Button: {
            primaryShadow: '0 4px 12px rgba(20, 120, 93, 0.16)',
            fontWeight: 600,
          },
          Layout: {
            bodyBg: '#f4f7f6',
            headerBg: '#ffffff',
            siderBg: '#f8faf9',
          },
          Menu: {
            itemBg: 'transparent',
            itemColor: '#435b54',
            itemHoverBg: '#edf4f1',
            itemHoverColor: '#172b26',
            itemSelectedBg: '#dfeee8',
            itemSelectedColor: '#0f674f',
            itemBorderRadius: 8,
          },
          Table: {
            headerBg: '#eaf1ee',
            headerColor: '#304b43',
            headerSplitColor: '#d9e3df',
            borderColor: '#e2e9e6',
            rowHoverBg: '#f3f8f6',
            cellPaddingBlock: 14,
            cellPaddingInline: 16,
          },
          Card: {
            colorBorderSecondary: '#dde6e2',
          },
          Input: {
            activeBorderColor: '#14785d',
            hoverBorderColor: '#589a85',
          },
          Select: {
            activeBorderColor: '#14785d',
            hoverBorderColor: '#589a85',
          },
        },
      }}
    >
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} future={{ v7_startTransition: true }} />
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  )
}

/**
 * 应用入口。
 * 主题：与 MES 登录页一致的制造现场青绿，安全橙仅用于告警和关键强调。
 * 与 ERP 的经营蓝保持明显区分，同时保持登录前后的品牌连续性。
 */
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
