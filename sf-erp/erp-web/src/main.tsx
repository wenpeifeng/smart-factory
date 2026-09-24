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
          colorPrimary: '#1668dc',
          colorInfo: '#1668dc',
          borderRadius: 8,
          fontSize: 14,
        },
      }}
    >
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  )
}

/**
 * 应用入口。
 * 主题：ERP 湛蓝主色（专业感），圆角 token 稍大以获得现代视觉，
 * 配合 Framer Motion 页面转场（见 layouts/MainLayout）形成"智能动感"风格。
 */
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
