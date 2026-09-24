/**
 * 报表中心「分组目录」SSR 断言脚本（对标 print 模块的 SSR 验证法）。
 * 用 vite.ssrLoadModule 加载真实 ReportCenter 组件 + react-dom/server renderToString，
 * 断言左侧分组目录渲染出 8 大分组与 14 张报表入口。
 * 运行：node scripts/ssr-report-center.mjs（cwd=erp-web）
 */
import { createServer } from 'vite'
import React from 'react'
import { renderToString } from 'react-dom/server'

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

  // 3. 14 张报表入口（4 张既有 + 10 张新增）
  const reports = [
    '经营月报', '销售毛利分析', '供应商绩效', '收发存月报', '呆滞物料', '库存库龄',
    '订单执行跟踪', '报工工时统计', '材料损耗分析', '财务汇总', '费用分析',
    '计件工资汇总', '销售明细', '采购明细',
  ]
  for (const r of reports) {
    assert(`报表「${r}」`, html.includes(r))
  }

  // 4. 菜单结构与懒加载 fallback
  assert('AntD Menu 渲染（ant-menu）', html.includes('ant-menu'))
  assert('默认选中经营月报（aria-selected）', /aria-selected="true"[^>]*title="经营月报"|title="经营月报"[^>]*aria-selected="true"/.test(html) || (html.includes('经营月报') && html.includes('ant-menu-item-selected')))
  assert('面板懒加载占位（报表加载中）', html.includes('报表加载中'))

  console.log(`\nHTML length=${html.length}`)
} finally {
  await vite.close()
}

console.log(failures === 0 ? '\nALL SSR ASSERTIONS PASSED' : `\n${failures} ASSERTIONS FAILED`)
process.exit(failures === 0 ? 0 : 1)
