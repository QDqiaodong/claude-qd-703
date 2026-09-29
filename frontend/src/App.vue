<template>
  <div class="shell">
    <!-- 顶部固定条：品牌 + 当前电梯上下文 + 计数器 -->
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark">🛗</span>
        <div>
          电梯维保（特种设备）管理平台
          <div class="brand-sub">
            {{ todayText }} · 在管电梯 {{ overview.elevatorCount || 0 }} 台 ·
            停用 {{ overview.outOfServiceCount || 0 }} 台
          </div>
        </div>
      </div>

      <div class="ctx-bar">
        <span>{{ buildingName || '未选楼宇' }}</span>
        <span class="ctx-sep">/</span>
        <span>{{ unitNo || '未选单元' }}</span>
        <span class="ctx-sep">/</span>
        <b>{{ currentElevator ? currentElevator.elevatorNo : '未选电梯' }}</b>
      </div>

      <div class="top-stats">
        <div class="stat-chip"><b>{{ overview.runningCount || 0 }}</b><span>运行中</span></div>
        <div class="stat-chip"><b>{{ overview.repairingCount || 0 }}</b><span>维修</span></div>
        <div class="stat-chip" :class="{ 'is-alert': (overview.emergencyActiveCount || 0) > 0 }">
          <b>{{ overview.emergencyActiveCount || 0 }}</b><span>应急维修中</span>
        </div>
        <div class="stat-chip"><b>{{ overview.inspectingCount || 0 }}</b><span>年检中</span></div>
        <div class="stat-chip" :class="{ 'is-alert': (overview.inspectDueCount || 0) > 0 }">
          <b>{{ overview.inspectDueCount || 0 }}</b><span>年检临期</span>
        </div>
        <div class="stat-chip"><b>{{ overview.planPendingCount || 0 }}</b><span>待执行计划</span></div>
        <div class="stat-chip" :class="{ 'is-alert': (overview.faultOpenCount || 0) > 0 }">
          <b>{{ overview.faultOpenCount || 0 }}</b><span>未闭环报修</span>
        </div>
        <div class="stat-chip" :class="{ 'is-alert': (overview.faultTrappedCount || 0) > 0 }">
          <b>{{ overview.faultTrappedCount || 0 }}</b><span>困人</span>
        </div>
      </div>
    </header>

    <div class="body">
      <!-- 左侧常驻三级级联面板：楼宇 → 单元 → 电梯 -->
      <aside class="cascade">
        <div class="cascade-title">🏢 楼宇 / 单元 / 电梯</div>
        <div class="cascade-hint">
          三级常驻面板：点任意一级即切换全局上下文，右侧五个模块全部跟着变。
        </div>

        <el-cascader-panel
          v-model="cascadeValue"
          :options="cascadeOptions"
          :props="cascadeProps"
          @change="onCascadeChange"
        />

        <div style="margin-top: 10px; display: flex; gap: 8px">
          <el-button size="small" @click="pickFirst">定位首台电梯</el-button>
          <el-button size="small" type="warning" plain @click="clearBuilding">清空楼宇过滤</el-button>
        </div>

        <!-- 过滤器开关对比：同一条查询方法，开/关 buildingFilter 结果集不同 -->
        <div class="filter-card">
          <h4>Hibernate 全局过滤器</h4>
          <div class="filter-row">
            <span>当前过滤器</span><b>{{ compare.filterName || '-' }}</b>
          </div>
          <div class="filter-row">
            <span>条件</span><b>{{ compare.filterCondition || '-' }}</b>
          </div>
          <div class="filter-row">
            <span>开过滤（{{ compare.filteredCount || 0 }} 台）</span>
            <b>关过滤（{{ compare.unfilteredCount || 0 }} 台）</b>
          </div>
          <div class="filter-code">{{ compare.queryMethod || '' }}</div>
          <div class="filter-list">
            <div v-for="code in compare.filteredCodes || []" :key="code">✓ {{ code }}</div>
          </div>
        </div>
      </aside>

      <main class="stage">
        <!-- 横向模块条 -->
        <nav class="navbar">
          <button
            v-for="item in navItems"
            :key="item.key"
            class="nav-btn"
            :class="{ 'is-on': activeKey === item.key }"
            type="button"
            :title="item.hint"
            @click="go(item.path)"
          >
            <span class="nav-ico">{{ item.icon }}</span>{{ item.label }}
          </button>
        </nav>

        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, onMounted, provide, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import router, { navItems } from './router'
import { buildingApi, elevatorApi, overviewApi } from './api'

/** main.js 只装了 Element Plus，router 在这里装到根实例上（install 自带幂等保护）。 */
getCurrentInstance().appContext.app.use(router)

const route = useRoute()
const nav = useRouter()

const tree = ref([])
const buildingId = ref(null)
const unitId = ref(null)
const elevatorId = ref(null)
const overview = ref({})
const compare = ref({})
const cascadeValue = ref([])

const cascadeProps = { expandTrigger: 'hover', checkStrictly: true }

const cascadeOptions = computed(() =>
  (tree.value || []).map((b) => ({
    value: 'B' + b.id,
    label: b.name,
    children: (b.units || []).map((u) => ({
      value: 'U' + u.id,
      label: u.unitNo,
      children: (u.elevators || []).map((e) => ({
        value: 'E' + e.id,
        label: e.elevatorNo + ' · ' + e.statusName
      }))
    }))
  }))
)

const buildingName = computed(() => {
  const hit = tree.value.find((b) => b.id === buildingId.value)
  return hit ? hit.name : ''
})

const unitNo = computed(() => {
  for (const b of tree.value) {
    const hit = (b.units || []).find((u) => u.id === unitId.value)
    if (hit) return hit.unitNo
  }
  return ''
})

const currentElevator = computed(() => {
  for (const b of tree.value) {
    for (const u of b.units || []) {
      const hit = (u.elevators || []).find((e) => e.id === elevatorId.value)
      if (hit) {
        return {
          id: hit.id,
          elevatorNo: hit.elevatorNo,
          buildingId: b.id,
          buildingName: b.name,
          unitId: u.id,
          unitNo: u.unitNo,
          brand: hit.brand,
          model: hit.model,
          floorStation: hit.floorStation,
          status: hit.status,
          statusName: hit.statusName,
          currentFloor: hit.currentFloor
        }
      }
    }
  }
  return null
})

const todayText = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})

const activeKey = computed(() => {
  const hit = navItems.find((item) => route.path.startsWith(item.path))
  return hit ? hit.key : 'dashboard'
})

/** 全局上下文：所有页面 inject('es')，左面板一改整站跟着变 */
const es = {
  buildingId,
  unitId,
  elevatorId,
  currentElevator,
  tree,
  overview,
  refreshOverview: loadOverview,
  reloadTree: loadTree
}
provide('es', es)

function go (path) {
  nav.push(path)
}

function onCascadeChange (values) {
  const list = values || []
  let b = null
  let u = null
  let e = null
  list.forEach((v) => {
    if (typeof v !== 'string') return
    if (v.startsWith('B')) b = Number(v.slice(1))
    if (v.startsWith('U')) u = Number(v.slice(1))
    if (v.startsWith('E')) e = Number(v.slice(1))
  })
  buildingId.value = b
  unitId.value = u
  elevatorId.value = e
  // 选中楼宇/单元时，若没点电梯就自动落到该分支第一台，保证右半边有内容
  if (!e) {
    const first = firstElevatorOf(b, u)
    if (first) {
      elevatorId.value = first.elevatorId
      unitId.value = first.unitId
      buildingId.value = first.buildingId
      cascadeValue.value = ['B' + first.buildingId, 'U' + first.unitId, 'E' + first.elevatorId]
    }
  }
  persist()
  loadCompare()
}

function firstElevatorOf (b, u) {
  for (const bd of tree.value) {
    if (b && bd.id !== b) continue
    for (const un of bd.units || []) {
      if (u && un.id !== u) continue
      const el = (un.elevators || [])[0]
      if (el) return { buildingId: bd.id, unitId: un.id, elevatorId: el.id }
    }
  }
  return null
}

function pickFirst () {
  const first = firstElevatorOf(null, null)
  if (!first) return
  buildingId.value = first.buildingId
  unitId.value = first.unitId
  elevatorId.value = first.elevatorId
  cascadeValue.value = ['B' + first.buildingId, 'U' + first.unitId, 'E' + first.elevatorId]
  persist()
  loadCompare()
}

function clearBuilding () {
  buildingId.value = null
  unitId.value = null
  elevatorId.value = null
  cascadeValue.value = []
  persist()
  loadCompare()
}

function persist () {
  localStorage.setItem('es-building-id', buildingId.value == null ? '' : String(buildingId.value))
  localStorage.setItem('es-unit-id', unitId.value == null ? '' : String(unitId.value))
  localStorage.setItem('es-elevator-id', elevatorId.value == null ? '' : String(elevatorId.value))
}

async function loadTree () {
  tree.value = await buildingApi.tree()
}

async function loadOverview () {
  overview.value = await overviewApi.get()
}

/** 过滤器开关对比：同一条查询方法返回两个不同大小的结果集 */
async function loadCompare () {
  compare.value = await elevatorApi.filterCompare(buildingId.value)
}

onMounted(async () => {
  await loadTree()
  await loadOverview()

  const sb = Number(localStorage.getItem('es-building-id'))
  const su = Number(localStorage.getItem('es-unit-id'))
  const se = Number(localStorage.getItem('es-elevator-id'))
  let hitB = tree.value.find((b) => b.id === sb)
  let hitU = null
  let hitE = null
  if (hitB) {
    hitU = (hitB.units || []).find((u) => u.id === su)
    if (hitU) hitE = (hitU.elevators || []).find((e) => e.id === se)
  }
  if (hitE) {
    buildingId.value = hitB.id
    unitId.value = hitU.id
    elevatorId.value = hitE.id
    cascadeValue.value = ['B' + hitB.id, 'U' + hitU.id, 'E' + hitE.id]
  } else {
    pickFirst()
  }
  await loadCompare()
})
</script>
