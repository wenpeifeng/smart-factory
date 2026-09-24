import type { ReactNode } from 'react'
import { usePerm } from '@/hooks/usePerm'
import { useTranslation } from 'react-i18next'

interface HasPermProps {
  /** 需要的权限码（如 'purchase:order:add'）；数组=任一满足即显示 */
  code: string | string[]
  /** 无权限时的替代内容（默认不渲染） */
  fallback?: ReactNode
  /** 无权限时展示统一提示文案（优先级低于自定义 fallback） */
  hint?: boolean
  children: ReactNode
}

/**
 * 权限显隐组件 —— 按钮/操作列等 UI 元素的统一权限控制。
 * <p>
 * 与后端 @RequirePerm 同一套权限码。所有受控按钮必须包裹本组件
 * （而非自行判断 perms 数组），保证全站控制方式唯一。
 *
 * @example
 * <HasPerm code="purchase:order:add">
 *   <Button type="primary">新增</Button>
 * </HasPerm>
 *
 * @example 无权限时展示提示（文案走 i18n：perm.noPerm）
 * <HasPerm code="purchase:order:add" hint>
 *   <Button type="primary">新增</Button>
 * </HasPerm>
 */
export default function HasPerm({ code, fallback, hint = false, children }: HasPermProps) {
  const { hasPerm, hasAnyPerm } = usePerm()
  const { t } = useTranslation()
  const allowed = Array.isArray(code) ? hasAnyPerm(...code) : hasPerm(code)
  if (allowed) return <>{children}</>
  if (fallback !== undefined) return <>{fallback}</>
  if (hint) return <span style={{ color: '#8c939c', fontSize: 12 }}>{t('perm.noPerm')}</span>
  return null
}
