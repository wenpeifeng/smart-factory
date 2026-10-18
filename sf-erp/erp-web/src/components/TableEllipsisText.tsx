import { Tooltip } from 'antd'

interface TableEllipsisTextProps {
  value?: string | number | null
}

/**
 * 表格长文本统一展示：保持单行，超出列宽显示省略号，悬停查看完整内容。
 */
export default function TableEllipsisText({ value }: TableEllipsisTextProps) {
  const text = value == null || value === '' ? '-' : String(value)

  if (text === '-') return <span>-</span>

  return (
    <Tooltip title={text} placement="topLeft">
      <span className="table-ellipsis-text">{text}</span>
    </Tooltip>
  )
}
