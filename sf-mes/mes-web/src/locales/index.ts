import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import zh from './zh.json'
import en from './en.json'

/** 语言持久化 key（localStorage） */
export const LANG_KEY = 'erp-lang'
/** 支持的语言 */
export type Lang = 'zh' | 'en'
export const LANGS: { value: Lang; label: string }[] = [
  { value: 'zh', label: '中文' },
  { value: 'en', label: 'English' },
]

function initialLang(): Lang {
  const saved = localStorage.getItem(LANG_KEY)
  if (saved === 'zh' || saved === 'en') return saved
  return 'zh'
}

/**
 * 前端 i18n 入口（react-i18next 标准）。
 * - 资源内置打包（zh 基准 / en），无后端加载延迟；
 * - 语言持久化 localStorage（erp-lang），默认 zh；
 * - 切换请用 changeLang()（同步持久化 + <html lang>），
 *   组件内用 useTranslation() 拿到的 t() 会即时刷新。
 */
i18n.use(initReactI18next).init({
  resources: {
    zh: { translation: zh },
    en: { translation: en },
  },
  lng: initialLang(),
  fallbackLng: 'zh',
  interpolation: { escapeValue: false }, // React 已防 XSS
})

/** 切换语言（持久化 + 文档语言标记同步） */
export function changeLang(lang: Lang) {
  localStorage.setItem(LANG_KEY, lang)
  document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en'
  void i18n.changeLanguage(lang)
}

/** axios 请求头用：zh → zh-CN，其余 → en（对齐后端 AcceptHeaderLocaleResolver） */
export function acceptLanguage(): string {
  return i18n.language.startsWith('zh') ? 'zh-CN' : 'en'
}

export default i18n
