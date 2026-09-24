import { useQuery } from '@tanstack/react-query'
import { Select, Tag } from 'antd'
import { getBooks } from '@/api/finance'

/** 账套切换下拉属性 */
interface BookSelectProps {
  /** 当前账套 ID */
  value: number
  /** 切换账套（页面自行失效缓存并重查） */
  onChange: (bookId: number) => void
  /** 宽度（默认 200） */
  width?: number
}

/**
 * 账套切换下拉（财务各页面筛选栏使用，页内切换、不动全局布局）。
 * <p>
 * 停用账套置灰不可选；默认账套带"默认"标记。
 * 切换动作由页面处理（更新模块级 setFinanceBookId + 失效查询缓存）。
 */
export default function BookSelect({ value, onChange, width = 200 }: BookSelectProps) {
  const { data: books, isLoading } = useQuery({ queryKey: ['finance', 'books'], queryFn: getBooks })

  return (
    <Select<number>
      style={{ width }}
      loading={isLoading}
      value={value}
      onChange={onChange}
      options={(books ?? []).map((b) => ({
        value: b.id,
        disabled: b.status !== 1,
        label: (
          <span>
            {b.name}（{b.code}）
            {b.isDefault === 1 ? (
              <Tag color="blue" style={{ marginLeft: 6 }}>
                默认
              </Tag>
            ) : null}
            {b.status !== 1 ? (
              <Tag color="default" style={{ marginLeft: 6 }}>
                停用
              </Tag>
            ) : null}
          </span>
        ),
      }))}
    />
  )
}
