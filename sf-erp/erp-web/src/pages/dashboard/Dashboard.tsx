/**
 * 工作台（首页驾驶舱）。
 *
 * 改造说明（report 模块）：原静态演示驾驶舱已由报表中心的真实数据版替代，
 * 此处选择【复用组件】而非重定向 —— 理由：
 * 1. /dashboard 是登录后的默认落地页（router index 直接渲染本组件），
 *    复用组件零跳转、无双次导航开销，且菜单「工作台」高亮状态保持正确；
 * 2. 数据为空/加载失败等状态由 ReportDashboard 内部统一处理（骨架/空态引导/错误兜底），
 *    重定向方案无法携带这些上下文，只是把同样的页面换个 URL。
 * 视觉与动效（错峰入场 / hover 上浮 / 渐变面积图）与原演示版完全一致。
 */
export { default } from '@/pages/report/dashboard/ReportDashboard'
