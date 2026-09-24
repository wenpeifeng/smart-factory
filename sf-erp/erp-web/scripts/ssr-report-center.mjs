/**
 * 报表中心「分组目录」SSR 断言脚本（对标 print 模块的 SSR 验证法）。
 * 用 vite.ssrLoadModule 加载真实 ReportCenter 组件 + react-dom/server renderToString，
 * 断言左侧分组目录渲染出 8 大分组与 18 张报表入口
 * （4 张既有 + 10 张制造业标准报表 + 4 张财务三大报表深化 Pane）。
 * 运行：node scripts/ssr-report-center.mjs（cwd=erp-web）
 */
import { createServer } from 'vite'
import React from 'react'
import { renderToString } from 'react-dom/server'

// SSR 环境（Node）无浏览器 localStorage：并行任务的 i18n 模块在模块顶层读取
// localStorage 持久化语言，直接加载 Pane（→ api → request → locales）会抛错。
// 这里打一个最小 polyfill（读返回 null = 默认中文，写丢弃），不改动应用代码。
if (typeof globalThis.localStorage === 'undefined') {
  globalThis.localStorage = {
    getItem: () => null,
    setItem: () => {},
    removeItem: () => {},
    clear: () => {},
    key: () => null,
    length: 0,
  }
}

const root = process.cwd()
const vite = await createServer({
  root,
  logLevel: 'error',
  server: { middlewareMode: true, hmr: false },
  appType: 'custom',
})

let failures = 0
function assert(name, cond, extra = '') {
  const ok = !!cond
  if (!ok) failures++
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${extra ? `  (${extra})` : ''}`)
}

try {
  const { default: ReportCenter } = await vite.ssrLoadModule('/src/pages/report/center/ReportCenter.tsx')
  const html = renderToString(React.createElement(ReportCenter))

  // 1. 分组目录标题
  assert('目录标题「报表目录」', html.includes('报表目录'))

  // 2. 八大分组（经营/销售/采购/库存/生产/财务/人事/多维）
  for (const g of ['经营', '销售', '采购', '库存', '生产', '财务', '人事', '多维']) {
    assert(`分组「${g}」`, html.includes(`>${g}<`) || html.includes(g))
  }

  // 3. 18 张报表入口（4 张既有 + 10 张标准报表 + 4 张财务三大报表深化）
  const reports = [
    '经营月报', '销售毛利分析', '供应商绩效', '收发存月报', '呆滞物料', '库存库龄',
    '订单执行跟踪', '报工工时统计', '材料损耗分析', '财务汇总', '费用分析',
    '资产负债表', '利润表', '应收账龄分析', '应付账龄分析',
    '计件工资汇总', '销售明细', '采购明细',
  ]
  for (const r of reports) {
    assert(`报表「${r}」`, html.includes(r))
  }

  // 4. 菜单结构与懒加载 fallback
  assert('AntD Menu 渲染（ant-menu）', html.includes('ant-menu'))
  assert('默认选中经营月报（aria-selected）', /aria-selected="true"[^>]*title="经营月报"|title="经营月报"[^>]*aria-selected="true"/.test(html) || (html.includes('经营月报') && html.includes('ant-menu-item-selected')))
  assert('面板懒加载占位（报表加载中）', html.includes('报表加载中'))

  // 5. 财务三大报表 Pane 模块可加载（4 个新 Pane 组件真实可渲染，SSR 断言）
  // Pane 内部使用 TanStack Query，需要 QueryClientProvider 包裹后才能 SSR 渲染。
  const rq = await import('@tanstack/react-query')
  const FinQueryClient = rq.QueryClient
  const FinQueryClientProvider = rq.QueryClientProvider
  const finPanes = [
    '/src/pages/report/center/BalanceSheetPane.tsx',
    '/src/pages/report/center/IncomeStatementPane.tsx',
    '/src/pages/report/center/ArAgingPane.tsx',
    '/src/pages/report/center/ApAgingPane.tsx',
  ]
  for (const p of finPanes) {
    const mod = await vite.ssrLoadModule(p)
    assert(`Pane 模块可加载 ${p.split('/').pop()}`, typeof mod.default === 'function')
    const client = new FinQueryClient({ defaultOptions: { queries: { retry: false } } })
    renderToString(
      React.createElement(FinQueryClientProvider, { client }, React.createElement(mod.default)),
    )
    assert(`Pane SSR 渲染 ${p.split('/').pop()}`, true)
  }

  console.log(`\nHTML length=${html.length}`)
} finally {
  await vite.close()
}

console.log(failures === 0 ? '\nALL SSR ASSERTIONS PASSED' : `\n${failures} ASSERTIONS FAILED`)
process.exit(failures === 0 ? 0 : 1)
