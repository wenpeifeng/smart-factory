import { useEffect, useState } from 'react'
import { Select, Spin } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { mrpApi, type MaterialBrief } from '@/api/mrp'

/** 物料搜索选择器 props */
interface MaterialSelectProps {
  value?: number
  onChange?: (value: number | undefined, material: MaterialBrief | undefined) => void
  placeholder?: string
  disabled?: boolean
  allowClear?: boolean
}

/**
 * 物料选择器：远程搜索 base_material（走 /mrp/materials 只读投影），
 * 300ms 防抖，选中后同时回调完整物料对象供页面展示。
 */
export default function MaterialSelect({
  value,
  onChange,
  placeholder,
  disabled,
  allowClear = true,
}: MaterialSelectProps) {
  const [keyword, setKeyword] = useState('')
  const [debounced, setDebounced] = useState('')

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(keyword), 300)
    return () => clearTimeout(timer)
  }, [keyword])

  const { data, isLoading } = useQuery({
    queryKey: ['mrp', 'materials', debounced],
    queryFn: () => mrpApi.materials({ pageNum: 1, pageSize: 50, keyword: debounced || undefined }),
  })

  const options = (data?.records ?? []).map((m) => ({
    value: m.id,
    label: `${m.code} | ${m.name}`,
    material: m,
  }))

  return (
    <Select
      showSearch
      allowClear={allowClear}
      filterOption={false}
      value={value}
      placeholder={placeholder ?? '编码 / 名称搜索物料'}
      onSearch={setKeyword}
      onChange={(v) => onChange?.(v, options.find((o) => o.value === v)?.material)}
      notFoundContent={isLoading ? <Spin size="small" /> : '暂无数据'}
      options={options}
      disabled={disabled}
      style={{ width: '100%' }}
    />
  )
}
