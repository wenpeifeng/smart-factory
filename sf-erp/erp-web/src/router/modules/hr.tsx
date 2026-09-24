import type { ComponentType } from 'react'

/**
 * 人力资源模块路由表（由主路由在集成时合并，path 无首斜杠）。
 * 页面路径与菜单一致：hr/employees | hr/attendance | hr/piecewage。
 */
export const hrRoutes: { path: string; lazy: () => Promise<{ default: ComponentType }> }[] = [
  {
    path: 'hr/employees',
    lazy: () => import('@/pages/hr/employees/EmployeeList'),
  },
  {
    path: 'hr/attendance',
    lazy: () => import('@/pages/hr/attendance/AttendancePage'),
  },
  {
    path: 'hr/piecewage',
    lazy: () => import('@/pages/hr/piecewage/PieceWagePage'),
  },
]
