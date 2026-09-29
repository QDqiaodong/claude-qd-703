import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一走 nginx 反代到后端 /api：
 *   成功 → 后端包成 { ok:true, message, data }，这里直接把 data 交给页面
 *   失败 → 后端包成 { ok:false, message:"中文业务提示" }，这里统一弹提示并 reject
 */
const http = axios.create({ baseURL: '/api', timeout: 15000 })

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'ok')) {
      if (body.ok) return body.data
      ElMessage.error(body.message || '操作失败')
      return Promise.reject(new Error(body.message || '操作失败'))
    }
    return body
  },
  (err) => {
    const body = err.response && err.response.data
    const message = (body && body.message) || err.message || '网络异常，请稍后再试'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  }
)

function paramsOf (buildingId, elevatorId) {
  const p = {}
  if (buildingId) p.buildingId = buildingId
  if (elevatorId) p.elevatorId = elevatorId
  return p
}

/** 模块一：楼宇与单元 */
export const buildingApi = {
  list: (buildingId) => http.get('/buildings', { params: paramsOf(buildingId) }),
  tree: (buildingId) => http.get('/buildings/tree', { params: paramsOf(buildingId) }),
  create: (data) => http.post('/buildings', data),
  setStatus: (id, status) => http.post(`/buildings/${id}/status`, { status })
}

export const unitApi = {
  list: (buildingId) => http.get('/units', { params: paramsOf(buildingId) }),
  create: (data) => http.post('/units', data),
  setStatus: (id, status) => http.post(`/units/${id}/status`, { status })
}

/** 模块二：电梯台账 */
export const elevatorApi = {
  list: (buildingId, unitId, elevatorId) => http.get('/elevators', {
    params: {
      ...paramsOf(buildingId, elevatorId),
      ...(unitId ? { unitId } : {})
    }
  }),
  /** 同一查询方法开关 buildingFilter 的对比结果 */
  filterCompare: (buildingId) => http.get('/elevators/filter-compare', { params: paramsOf(buildingId) }),
  shaft: (id) => http.get(`/elevators/${id}/shaft`),
  create: (data) => http.post('/elevators', data),
  setStatus: (id, status) => http.post(`/elevators/${id}/status`, { status }),
  dock: (id, floor) => http.post(`/elevators/${id}/dock`, { floor })
}

/** 模块三：维保计划 */
export const planApi = {
  list: (buildingId, elevatorId) => http.get('/plans', { params: paramsOf(buildingId, elevatorId) }),
  create: (data) => http.post('/plans', data),
  execute: (id, data) => http.post(`/plans/${id}/execute`, data),
  delay: (id, days) => http.post(`/plans/${id}/delay`, days ? { days } : {}),
  void: (id) => http.post(`/plans/${id}/void`)
}

/** 模块三：维保工单 */
export const orderApi = {
  list: (buildingId, elevatorId) => http.get('/orders', { params: paramsOf(buildingId, elevatorId) }),
  create: (data) => http.post('/orders', data),
  execute: (id, data) => http.post(`/orders/${id}/execute`, data),
  delay: (id, days) => http.post(`/orders/${id}/delay`, days ? { days } : {}),
  void: (id) => http.post(`/orders/${id}/void`)
}

/** 模块四：故障报修（含应急联动） */
export const faultApi = {
  list: (buildingId, elevatorId) => http.get('/faults', { params: paramsOf(buildingId, elevatorId) }),
  create: (data) => http.post('/faults', data),
  advance: (id) => http.post(`/faults/${id}/advance`),
  fix: (id, handleResult) => http.post(`/faults/${id}/fix`, { handleResult }),
  /** 受理应急报修：自动生成抢修工单、电梯进入应急维修 */
  accept: (id, data) => http.post(`/faults/${id}/accept`, data || {}),
  /** 处置：处理中 → 待验收（处理结果 + 实际到场时间） */
  process: (id, data) => http.post(`/faults/${id}/process`, data),
  /** 验收闭环：待验收 → 已修复（确认电梯恢复运行） */
  close: (id, data) => http.post(`/faults/${id}/close`, data),
  /** 误报撤销：必须填撤销原因，进入「已撤销」终态 */
  cancel: (id, data) => http.post(`/faults/${id}/cancel`, data),
  logs: (id) => http.get(`/faults/${id}/logs`)
}

/** 模块五：配件更换 */
export const partApi = {
  list: (buildingId, elevatorId) => http.get('/parts', { params: paramsOf(buildingId, elevatorId) }),
  summary: (buildingId) => http.get('/parts/summary', { params: paramsOf(buildingId) }),
  create: (data) => http.post('/parts', data)
}

/** 顶栏总览计数器 */
export const overviewApi = {
  get: () => http.get('/overview')
}
