import { Tooltip } from 'antd'

interface TableEllipsisTextProps {
  value?: string | number | null
}

/** 表格长文本统一单行省略，悬停展示完整内容。 */
export default function TableEllipsisText({ value }: TableEllipsisTextProps) {
  const text = value == null || value === '' ? '-' : String(value)
  if (text === '-') return <span>-</span>
  return (
    <Tooltip title={text} placement="topLeft">
      <span className="table-ellipsis-text">{text}</span>
    </Tooltip>
  )
}
