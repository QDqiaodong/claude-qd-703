<template>
  <div>
    <div class="split">
      <!-- 左半：电梯井道纵向剖面图（纯 SVG 手绘） -->
      <section class="panel">
        <div class="panel-head">
          <h3>井道纵向剖面</h3>
          <span class="panel-sub">
            {{ ev ? ev.elevatorNo + ' · ' + ev.brand + ' ' + ev.model : '未选电梯' }} ·
            {{ shaft.floorCount || 0 }} 层 {{ shaft.currentFloor || 1 }} 站
          </span>
          <div class="panel-extra">
            <el-select
              v-model="dockFloor"
              size="small"
              style="width: 118px"
              placeholder="停靠层"
              @change="doDock"
            >
              <el-option v-for="f in floorList" :key="f" :label="f + ' 层'" :value="f" />
            </el-select>
          </div>
        </div>

        <svg v-if="floorList.length" class="shaft-svg" :viewBox="`0 0 300 ${svgHeight}`">
          <!-- 井道外墙 -->
          <rect
            :x="shaftX"
            :y="top - 8"
            :width="shaftW"
            :height="plotH + 16"
            fill="#fffaf5"
            stroke="#e0cbb4"
            stroke-width="1.2"
          />
          <!-- 底坑 -->
          <line
            :x1="shaftX"
            :y1="top + plotH + 8"
            :x2="shaftX + shaftW"
            :y2="top + plotH + 8"
            stroke="#8d6e63"
            stroke-width="2"
          />
          <text :x="shaftX + 4" :y="top + plotH + 22" class="floor-label">底坑</text>

          <!-- 钢丝绳：两条竖线 -->
          <line
            :x1="ropeX1"
            :y1="top - 8"
            :x2="ropeX1"
            :y2="top + plotH + 8"
            stroke="#795548"
            stroke-width="1.2"
          />
          <line
            :x1="ropeX2"
            :y1="top - 8"
            :x2="ropeX2"
            :y2="top + plotH + 8"
            stroke="#795548"
            stroke-width="1.2"
          />

          <!-- 逐层：横线 + 层号 + 门开口小竖线 + 点击热区 -->
          <g v-for="f in floorList" :key="f">
            <line
              :x1="labelX + 4"
              :y1="lineY(f)"
              :x2="shaftX + shaftW"
              :y2="lineY(f)"
              stroke="#f0dcc8"
              stroke-width="1"
            />
            <text
              :x="labelX"
              :y="lineY(f) + labelDy"
              text-anchor="end"
              class="floor-label"
              :class="{ 'is-current': f === shaft.currentFloor }"
            >{{ f }}</text>
            <!-- 门开口 -->
            <line
              :x1="doorX"
              :y1="lineY(f) - cellH * 0.32"
              :x2="doorX"
              :y2="lineY(f) + cellH * 0.32"
              stroke="#ef6c00"
              stroke-width="2"
            />
            <rect
              class="floor-hit"
              :x="labelX - 14"
              :y="lineY(f) - cellH / 2"
              :width="doorX - labelX + 26"
              :height="cellH"
              fill="transparent"
              @click="dockTo(f)"
            >
              <title>点击让轿厢停靠 {{ f }} 层</title>
            </rect>
          </g>

          <!-- 对重块（与轿厢反向） -->
          <rect
            :x="cwX"
            :y="counterweightY"
            :width="cwW"
            :height="cellH * 0.72"
            fill="#8d6e63"
            rx="2"
          />
          <text :x="cwX + cwW + 4" :y="counterweightY + cellH * 0.6" class="floor-label">对重</text>

          <!-- 轿厢 -->
          <rect
            :x="carX"
            :y="carY"
            :width="carW"
            :height="cellH * 0.84"
            :fill="carColor"
            rx="3"
            stroke="rgba(0,0,0,0.15)"
          />
          <text
            :x="carX + carW / 2"
            :y="carY + cellH * 0.58"
            text-anchor="middle"
            class="shaft-dock-text"
          >{{ shaft.currentFloor || 1 }}F</text>
        </svg>

        <div class="legend">
          <span><i style="background: #ef6c00"></i>运行</span>
          <span><i style="background: #ff9800"></i>维修</span>
          <span><i style="background: #1e88e5"></i>年检中</span>
          <span><i style="background: #9e9e9e"></i>停用</span>
          <span class="muted">点任意楼层横线即可让轿厢停靠</span>
        </div>

        <div class="kv-grid" style="margin-top: 12px">
          <div class="kv"><span>额定载重</span><b>{{ ev ? ev.ratedLoad + ' kg' : '-' }}</b></div>
          <div class="kv"><span>额定速度</span><b>{{ ev ? ev.ratedSpeed + ' m/s' : '-' }}</b></div>
          <div class="kv"><span>层站数</span><b>{{ shaft.floorCount || '-' }}</b></div>
          <div class="kv"><span>当前状态</span><b>{{ shaft.statusName || '-' }}</b></div>
          <div class="kv"><span>上次年检</span><b>{{ ev ? ev.lastInspectDate || '-' : '-' }}</b></div>
          <div class="kv">
            <span>下次年检</span>
            <b :style="{ color: dueSoon ? '#c62828' : '#2c2118' }">
              {{ ev ? ev.nextInspectDate || '-' : '-' }}
              <span v-if="dueSoon" style="font-size: 12px">（{{ ev.inspectDueDays }} 天）</span>
            </b>
          </div>
        </div>
      </section>

      <!-- 右半：维保计划甘特条（纯 CSS 横向条） -->
      <section class="panel">
        <div class="panel-head">
          <h3>维保计划甘特条</h3>
          <span class="panel-sub">近 12 个月 · 按维保类型上色 · 按状态加边框</span>
          <div class="panel-extra">
            <el-tag size="small" type="info">{{ planRows.length }} 条计划</el-tag>
          </div>
        </div>

        <div class="gantt">
          <div class="gantt-head">
            <div class="gantt-name">计划编号 / 周期</div>
            <div class="gantt-track">
              <div
                v-for="m in months"
                :key="m.key"
                class="gantt-month"
                :class="{ 'is-now': m.isNow }"
              >{{ m.label }}</div>
            </div>
          </div>

          <div v-if="!planRows.length" class="gantt-empty">当前范围没有维保计划</div>

          <div v-for="row in planRows" :key="row.id" class="gantt-row">
            <div class="gantt-name">
              {{ row.planNo }}
              <span>{{ row.cycleName }} · {{ row.statusName }}</span>
            </div>
            <div class="gantt-track">
              <div
                v-for="m in months"
                :key="m.key"
                class="gantt-month"
                style="border-right: 1px dashed #f6e7d6"
              ></div>
              <el-tooltip
                v-if="row.offset >= 0"
                placement="top"
                :content="row.tip"
              >
                <div
                  class="gantt-bar"
                  :class="row.statusClass"
                  :style="{ left: row.left, width: barWidth, background: row.color }"
                >{{ row.cycleName }} · {{ row.planDate }}</div>
              </el-tooltip>
              <span
                v-else
                class="muted"
                style="position: absolute; left: 8px; top: 5px; font-size: 11px"
              >不在近 12 个月内（{{ row.planDate }}）</span>
            </div>
          </div>
        </div>

        <div class="legend">
          <span><i style="background: #ef6c00"></i>半月</span>
          <span><i style="background: #ff9800"></i>月度</span>
          <span><i style="background: #8e24aa"></i>季度</span>
          <span><i style="background: #00897b"></i>半年</span>
          <span><i style="background: #1565c0"></i>年度</span>
          <span><i style="background: #ef6c00; border-style: dashed"></i>待执行</span>
          <span>
            <i style="background: repeating-linear-gradient(45deg, #ff9800 0 4px, #fff 4px 8px)"></i>已延期
          </span>
          <span><i style="background: #bdbdbd"></i>作废</span>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { elevatorApi, planApi } from '../api'
import { ElMessage } from 'element-plus'

const es = inject('es')
const shaft = ref({})
const plans = ref([])
const dockFloor = ref(1)

const CYCLE_COLOR = {
  HALF_MONTH: '#ef6c00',
  MONTH: '#ff9800',
  QUARTER: '#8e24aa',
  HALF_YEAR: '#00897b',
  YEAR: '#1565c0'
}
const STATUS_CLASS = {
  PENDING: 'is-pending',
  DONE: '',
  DELAYED: 'is-delayed',
  VOID: 'is-void'
}

// ---- 井道几何 ----
const top = 26
const labelX = 26
const shaftX = 32
const shaftW = 118
const doorX = 158
const ropeX1 = 196
const ropeX2 = 206
const cwX = 190
const cwW = 26
const carX = 52
const carW = 78

const floorCount = computed(() => shaft.value.floorCount || 1)
const cellH = computed(() => Math.min(30, Math.max(11, 470 / floorCount.value)))
const plotH = computed(() => cellH.value * floorCount.value)
const svgHeight = computed(() => plotH.value + 60)
const labelDy = computed(() => Math.min(4, cellH.value * 0.36))

const floorList = computed(() => {
  const n = floorCount.value
  const list = []
  for (let i = n; i >= 1; i--) list.push(i)
  return list
})

function lineY (floor) {
  return top + (floorCount.value - floor) * cellH.value + cellH.value / 2
}

const carY = computed(() => lineY(shaft.value.currentFloor || 1) - cellH.value * 0.42)
const counterweightY = computed(
  () => top + plotH.value - (lineY(shaft.value.currentFloor || 1) - top) - cellH.value * 0.36
)
const carColor = computed(
  () => ({
    RUNNING: '#ef6c00',
    REPAIRING: '#ff9800',
    INSPECTING: '#1e88e5',
    EMERGENCY_REPAIR: '#c62828',
    OUT_OF_SERVICE: '#9e9e9e'
  })[shaft.value.status] || '#9e9e9e'
)

const ev = computed(() => shaft.value.elevator || null)
const dueSoon = computed(() => ev.value && ev.value.inspectDueDays != null && ev.value.inspectDueDays < 60)

// ---- 甘特条：默认近 12 个月，计划跨度更大时自动放宽到最多 16 列 ----
const months = computed(() => {
  const now = new Date()
  const cur = now.getFullYear() * 12 + now.getMonth()
  const offsets = (plans.value || [])
    .filter((p) => p.planMonth)
    .map((p) => {
      const [y, m] = p.planMonth.split('-').map(Number)
      return y * 12 + (m - 1) - cur
    })
  let start = -8
  let count = 12
  if (offsets.length) {
    const min = Math.min(...offsets)
    const max = Math.max(...offsets)
    count = Math.min(16, Math.max(12, max - min + 1))
    start = Math.min(min, Math.max(max - count + 1, -8))
    if (start > 0) start = 0
    if (start + count - 1 < 0) start = -count + 1
  }
  const list = []
  for (let i = 0; i < count; i++) {
    const offset = start + i
    const d = new Date(now.getFullYear(), now.getMonth() + offset, 1)
    list.push({
      key: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`,
      label: `${d.getFullYear() % 100}/${d.getMonth() + 1}`,
      isNow: offset === 0
    })
  }
  return list
})

const barWidth = computed(() => `${(100 / months.value.length).toFixed(4)}%`)

const planRows = computed(() => {
  const keys = months.value.map((m) => m.key)
  return (plans.value || []).slice(0, 40).map((p) => {
    const offset = p.planMonth ? keys.indexOf(p.planMonth) : -1
    return {
      id: p.id,
      planNo: p.planNo,
      cycleName: p.cycleName,
      statusName: p.statusName,
      planDate: p.planDate,
      offset,
      left: `${((offset * 100) / months.value.length).toFixed(4)}%`,
      color: CYCLE_COLOR[p.cycle] || '#ef6c00',
      statusClass: STATUS_CLASS[p.status] || '',
      tip: `${p.planNo} · ${p.elevatorNo}\n${p.cycleName}维保 · ${p.planDate}\n${p.statusName} · ${p.team || '未派班组'} ${p.worker || ''}\n${p.remark || ''}`
    }
  })
})

async function loadShaft () {
  if (!es.elevatorId.value) {
    shaft.value = {}
    return
  }
  shaft.value = await elevatorApi.shaft(es.elevatorId.value)
  dockFloor.value = shaft.value.currentFloor || 1
}

async function loadPlans () {
  plans.value = await planApi.list(es.buildingId.value, es.elevatorId.value)
}

async function dockTo (floor) {
  if (!es.elevatorId.value) return
  if (shaft.value.status === 'OUT_OF_SERVICE') {
    ElMessage.warning('电梯已停用，不能调整轿厢位置')
    return
  }
  const updated = await elevatorApi.dock(es.elevatorId.value, floor)
  shaft.value = { ...shaft.value, currentFloor: updated.currentFloor }
  dockFloor.value = updated.currentFloor
  ElMessage.success(`轿厢已停靠 ${floor} 层`)
}

function doDock (floor) {
  dockTo(floor)
}

onMounted(async () => {
  await loadShaft()
  await loadPlans()
})

watch([es.buildingId, es.elevatorId], async () => {
  await loadShaft()
  await loadPlans()
})
</script>
