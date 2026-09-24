/** 质量模块路由片段（检验模板/检验单/不合格处置） */
export const qualityRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: 'quality/templates', lazy: () => import('@/pages/quality/templates/TemplateList') },
  { path: 'quality/inspects', lazy: () => import('@/pages/quality/inspects/InspectList') },
  { path: 'quality/rejects', lazy: () => import('@/pages/quality/rejects/RejectList') },
  { path: 'quality/spc', lazy: () => import('@/pages/quality/spc/SpcPage') },
]
