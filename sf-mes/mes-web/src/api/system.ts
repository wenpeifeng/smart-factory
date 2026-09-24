import { get, post, put, del, type PageResult } from './request'

/* ===================== 类型定义（与后端 VO 对齐） ===================== */

/** 用户（后端 UserVO，永不包含 password） */
export interface SysUser {
  employeeNo?: string | null
  id: number
  username: string
  nickname: string
  phone: string | null
  email: string | null
  deptId: number | null
  deptName: string | null
  status: number
  remark: string | null
  createTime: string
  roleIds: number[] | null
  roleNames: string[] | null
}

/** 用户新增/修改表单 */
export interface SysUserForm {
  id?: number
  username: string
  nickname: string
  password?: string
  phone?: string
  email?: string
  deptId?: number | null
  status?: number
  remark?: string
  roleIds?: number[]
}

/** 角色（后端 RoleVO） */
export interface SysRole {
  id: number
  name: string
  code: string
  sort: number
  status: number
  remark: string | null
  createTime: string
}

/** 角色表单 */
export interface SysRoleForm {
  id?: number
  name: string
  code: string
  sort?: number
  status?: number
  remark?: string
}

/** 菜单树节点（后端 MenuVO） */
export interface SysMenu {
  id: number
  parentId: number
  name: string
  type: number // 1目录 2菜单 3按钮
  path: string | null
  component: string | null
  perms: string | null
  icon: string | null
  sort: number
  visible: number
  status: number
  createTime: string
  children: SysMenu[]
}

/** 菜单表单 */
export interface SysMenuForm {
  id?: number
  parentId: number
  name: string
  type: number
  path?: string
  component?: string
  perms?: string
  icon?: string
  sort?: number
  visible?: number
  status?: number
}

/** 部门树节点（后端 DeptVO） */
export interface SysDept {
  id: number
  parentId: number
  name: string
  sort: number
  leader: string | null
  phone: string | null
  status: number
  createTime: string
  children: SysDept[]
}

/** 部门表单 */
export interface SysDeptForm {
  id?: number
  parentId: number
  name: string
  sort?: number
  leader?: string
  phone?: string
  status?: number
}

/** 字典类型（后端 DictTypeVO） */
export interface SysDictType {
  id: number
  name: string
  type: string
  status: number
  remark: string | null
  createTime: string
}

/** 字典类型表单 */
export interface SysDictTypeForm {
  id?: number
  name: string
  type: string
  status?: number
  remark?: string
}

/** 字典项（后端 DictDataVO） */
export interface SysDictData {
  id: number
  dictType: string
  label: string
  value: string
  sort: number
  status: number
  remark: string | null
  createTime: string
}

/** 字典项表单 */
export interface SysDictDataForm {
  id?: number
  dictType: string
  label: string
  value: string
  sort?: number
  status?: number
  remark?: string
}

/** 公开字典选项（GET /dict/{type} 返回项） */
export interface DictItem {
  label: string
  value: string
  sort: number
}

/** 操作日志（后端 OperLogVO） */
export interface SysOperLog {
  id: number
  module: string
  operation: string
  method: string | null
  requestMethod: string | null
  requestUrl: string | null
  requestParams: string | null
  operatorId: number | null
  operatorName: string | null
  ip: string | null
  costMs: number | null
  status: number
  errorMsg: string | null
  createTime: string
}

/**
 * 分页查询公共参数。
 * 注意用 type 而非 interface：对象字面量类型才有隐式索引签名，
 * 才能赋给 request.ts 的 Record<string, unknown> 参数。
 */
export type PageParams = { pageNum: number; pageSize: number }

/* ===================== 认证 ===================== */

/** 登录返回（token + 用户信息） */
export interface LoginResp {
  token: string
  user: SysUser
}

/** POST /auth/login 登录 */
export const login = (data: { username: string; password: string }) =>
  post<LoginResp>('/auth/login', data)

/** POST /auth/logout 登出（Redis 黑名单） */
export const logout = () => post<void>('/auth/logout')

/** GET /auth/me 当前用户上下文 */
export const getMe = () =>
  get<{ user: SysUser; roles: string[]; perms: string[] }>('/auth/me')

/** GET /auth/menus 当前用户菜单树 */
export const getMyMenus = () => get<SysMenu[]>('/auth/menus')

/* ===================== 用户管理 ===================== */

/** GET /system/users 分页 */
export const pageUsers = (params: {
  pageNum: number
  pageSize: number
  keyword?: string
  deptId?: number
  status?: number
}) => get<PageResult<SysUser>>('/system/users', params)

/** GET /system/users/{id} 详情 */
export const getUser = (id: number) => get<SysUser>(`/system/users/${id}`)

/** POST /system/users 新增 */

/** PUT /system/users/{id} 修改 */

/** DELETE /system/users/{id} 删除 */

/** PUT /system/users/{id}/password 重置密码 */
export const resetUserPassword = (id: number, password: string) =>
  put<void>(`/system/users/${id}/password`, { password })

/** PUT /system/users/{id}/status 启停 */
export const updateUserStatus = (id: number, status: number) =>
  put<void>(`/system/users/${id}/status`, { status })

/** PUT /system/users/{id}/roles 分配角色 */
export const assignUserRoles = (id: number, roleIds: number[]) =>
  put<void>(`/system/users/${id}/roles`, { roleIds })

/* ===================== 角色管理 ===================== */

/** GET /system/roles 分页 */
export const pageRoles = (params: {
  pageNum: number
  pageSize: number
  keyword?: string
  status?: number
}) => get<PageResult<SysRole>>('/system/roles', params)

/** GET /system/roles/all 全量（下拉） */
export const listAllRoles = () => get<SysRole[]>('/system/roles/all')

/** GET /system/roles/{id} 详情 */
export const getRole = (id: number) => get<SysRole>(`/system/roles/${id}`)

/** POST /system/roles 新增 */
export const createRole = (data: SysRoleForm) => post<number>('/system/roles', data)

/** PUT /system/roles/{id} 修改 */
export const updateRole = (id: number, data: SysRoleForm) => put<void>(`/system/roles/${id}`, data)

/** DELETE /system/roles/{id} 删除 */
export const deleteRole = (id: number) => del<void>(`/system/roles/${id}`)

/** GET /system/roles/{id}/menus 已分配菜单 ID */
export const getRoleMenuIds = (id: number) => get<number[]>(`/system/roles/${id}/menus`)

/** PUT /system/roles/{id}/menus 分配菜单 */
export const assignRoleMenus = (id: number, menuIds: number[]) =>
  put<void>(`/system/roles/${id}/menus`, { menuIds })

/* ===================== 菜单管理 ===================== */

/** GET /system/menus 菜单树 */
export const getMenuTree = (keyword?: string) =>
  get<SysMenu[]>('/system/menus', keyword ? { keyword } : undefined)

/** POST /system/menus 新增 */
export const createMenu = (data: SysMenuForm) => post<number>('/system/menus', data)

/** PUT /system/menus/{id} 修改 */
export const updateMenu = (id: number, data: SysMenuForm) => put<void>(`/system/menus/${id}`, data)

/** DELETE /system/menus/{id} 删除 */
export const deleteMenu = (id: number) => del<void>(`/system/menus/${id}`)

/* ===================== 部门管理 ===================== */

/** GET /system/depts 部门树 */
export const getDeptTree = (keyword?: string) =>
  get<SysDept[]>('/system/depts', keyword ? { keyword } : undefined)

/** POST /system/depts 新增 */
export const createDept = (data: SysDeptForm) => post<number>('/system/depts', data)

/** PUT /system/depts/{id} 修改 */
export const updateDept = (id: number, data: SysDeptForm) => put<void>(`/system/depts/${id}`, data)

/** DELETE /system/depts/{id} 删除 */
export const deleteDept = (id: number) => del<void>(`/system/depts/${id}`)

/* ===================== 字典管理 ===================== */

/** GET /system/dict/types 分页 */
export const pageDictTypes = (params: {
  pageNum: number
  pageSize: number
  keyword?: string
  status?: number
}) => get<PageResult<SysDictType>>('/system/dict/types', params)

/** POST /system/dict/types 新增 */
export const createDictType = (data: SysDictTypeForm) => post<number>('/system/dict/types', data)

/** PUT /system/dict/types/{id} 修改 */
export const updateDictType = (id: number, data: SysDictTypeForm) =>
  put<void>(`/system/dict/types/${id}`, data)

/** DELETE /system/dict/types/{id} 删除 */
export const deleteDictType = (id: number) => del<void>(`/system/dict/types/${id}`)

/** GET /system/dict/datas 字典项分页 */
export const pageDictDatas = (params: {
  pageNum: number
  pageSize: number
  dictType?: string
  keyword?: string
  status?: number
}) => get<PageResult<SysDictData>>('/system/dict/datas', params)

/** POST /system/dict/datas 新增字典项 */
export const createDictData = (data: SysDictDataForm) => post<number>('/system/dict/datas', data)

/** PUT /system/dict/datas/{id} 修改字典项 */
export const updateDictData = (id: number, data: SysDictDataForm) =>
  put<void>(`/system/dict/datas/${id}`, data)

/** DELETE /system/dict/datas/{id} 删除字典项 */
export const deleteDictData = (id: number) => del<void>(`/system/dict/datas/${id}`)

/** GET /dict/{type} 公开字典选项（其他模块下拉数据源） */
export const getDictByType = (type: string) => get<DictItem[]>(`/dict/${type}`)

/* ===================== 操作日志 ===================== */

/** GET /system/logs 分页 */
export const pageOperLogs = (params: {
  pageNum: number
  pageSize: number
  module?: string
  operatorName?: string
  status?: number
  beginTime?: string
  endTime?: string
}) => get<PageResult<SysOperLog>>('/system/logs', params)
