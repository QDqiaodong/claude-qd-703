import { createRouter, createWebHistory } from 'vue-router'

/**
 * 导航形态：左侧常驻三级级联面板（楼宇 → 单元 → 电梯）切换全局电梯上下文，
 * 顶部一条横向模块条在五个业务模块之间切换，所有模块共享同一台电梯。
 */
export const navItems = [
  {
    key: 'dashboard',
    path: '/dashboard',
    icon: '🛗',
    label: '井道与计划',
    hint: 'SVG 手绘井道剖面 + 维保计划甘特条'
  },
  {
    key: 'buildings',
    path: '/buildings',
    icon: '🏢',
    label: '楼宇与单元',
    hint: '楼宇档案·层数·建成年代·物业联系人'
  },
  {
    key: 'elevators',
    path: '/elevators',
    icon: '📋',
    label: '电梯台账',
    hint: '编号·品牌·额定载重速度·年检日·状态'
  },
  {
    key: 'orders',
    path: '/orders',
    icon: '🧾',
    label: '维保工单',
    hint: '计划与工单·执行 / 延期 / 作废'
  },
  {
    key: 'faults',
    path: '/faults',
    icon: '🚨',
    label: '故障报修',
    hint: '困人标红·响应与到达时长·状态推进'
  },
  {
    key: 'parts',
    path: '/parts',
    icon: '🔧',
    label: '配件更换',
    hint: '金额汇总·关联工单或报修单'
  }
]

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue') },
  { path: '/buildings', name: 'buildings', component: () => import('../views/BuildingView.vue') },
  { path: '/elevators', name: 'elevators', component: () => import('../views/ElevatorView.vue') },
  { path: '/orders', name: 'orders', component: () => import('../views/OrderView.vue') },
  { path: '/faults', name: 'faults', component: () => import('../views/FaultView.vue') },
  { path: '/parts', name: 'parts', component: () => import('../views/PartView.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
