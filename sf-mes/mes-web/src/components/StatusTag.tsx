import { Tag } from 'antd'

/**
 * 单据/数据状态语义 —— 全系统统一。
 * <p>
 * 各模块状态机五花八门（草稿/待审核/已审核/审批中/已关闭/已完成...），
 * 但语义收敛到五种：DEFAULT 中性 / PROCESS 进行中 / SUCCESS 完成 / WARNING 异常注意 / ERROR 失败终止。
 * 页面只管把业务状态映射成语义，颜色由本组件统一渲染，保证全站视觉一致。
 */
export type StatusSemantic = 'default' | 'processing' | 'success' | 'warning' | 'error'

/** 业务状态 -> 语义 的推荐映射（不满足时页面可传自定义 semantic） */
export const STATUS_SEMANTIC: Record<string, StatusSemantic> = {
  // 通用
  草稿: 'default',
  待审核: 'processing',
  审批中: 'processing',
  已审核: 'success',
  已完成: 'success',
  已关闭: 'default',
  已取消: 'default',
  进行中: 'processing',
  加工中: 'processing',
  待维修: 'warning',
  维修中: 'processing',
  已修复: 'success',
  // 质量
  合格: 'success',
  不合格: 'error',
  让步接收: 'warning',
  // 生效状态
  启用: 'success',
  停用: 'default',
  在职: 'success',
  离职: 'default',
  在用: 'success',
  闲置: 'default',
  报废: 'error',
  // 财务
  未结算: 'warning',
  部分结算: 'processing',
  已结算: 'success',
  未开票: 'warning',
  已开票: 'success',
  已红冲: 'error',
}

interface StatusTagProps {
  /** 状态文案（先查 STATUS_SEMANTIC 自动映射，映射不到按 processing 处理） */
  value: string | number | null | undefined
  /** 可选：显式指定语义，覆盖自动映射 */
  semantic?: StatusSemantic
}

/** 语义对应的 Tag 颜色（全站唯一定义处） */
const SEMANTIC_COLOR: Record<StatusSemantic, string> = {
  default: 'default',
  processing: 'processing',
  success: 'success',
  warning: 'warning',
  error: 'error',
}

/**
 * 统一状态标签 —— 全系统所有状态列/状态展示必须用它。
 *
 * @example
 * // 表格列里
 * { title: '状态', dataIndex: 'statusText', render: (v) => <StatusTag value={v} /> }
 * // 特殊语义
 * <StatusTag value="超载" semantic="error" />
 */
export default function StatusTag({ value, semantic }: StatusTagProps) {
  if (value === null || value === undefined || value === '') {
    return <Tag>-</Tag>
  }
  const sem = semantic ?? STATUS_SEMANTIC[String(value)] ?? 'processing'
  return <Tag color={SEMANTIC_COLOR[sem]}>{String(value)}</Tag>
}
