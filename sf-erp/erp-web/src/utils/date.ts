import dayjs from 'dayjs'

/**
 * ERP 列表和详情的统一日期时间格式。
 * 后端可能返回 ISO 8601（含 T/毫秒）或普通日期时间，界面统一去掉 T 和毫秒。
 */
export function formatDateTime(value?: string | null): string {
  if (!value) return '-'

  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : value
}
