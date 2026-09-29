<template>
  <div>
    <!-- 维保计划 -->
    <section class="panel">
      <div class="panel-head">
        <h3>维保计划</h3>
        <span class="panel-sub">按周期排程 · 可执行 / 延期 / 作废</span>
      </div>

      <el-table :data="plans" size="small" border stripe>
        <el-table-column prop="planNo" label="计划编号" width="120" />
        <el-table-column prop="elevatorNo" label="电梯" width="120" />
        <el-table-column prop="buildingName" label="楼宇" width="130" />
        <el-table-column label="周期" width="80">
          <template #default="{ row }">
            <el-tag size="small" :color="cycleColor(row.cycle)" style="color: #fff">{{ row.cycleName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="planDate" label="计划执行日" width="110" />
        <el-table-column prop="team" label="维保班组" width="90" />
        <el-table-column prop="worker" label="维保员" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注 / 占用原因" min-width="200">
          <template #default="{ row }">
            <div>{{ row.remark }}</div>
            <div v-if="row.occupied" style="color: #c62828; font-size: 12px">⛔ {{ row.occupiedReason }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210">
          <template #default="{ row }">
            <el-button size="small" type="success" plain :disabled="isPlanDone(row)" @click="executePlan(row)">
              执行
            </el-button>
            <el-button size="small" type="warning" plain :disabled="isPlanDone(row)" @click="delayPlan(row)">
              延期
            </el-button>
            <el-button size="small" type="info" plain :disabled="isPlanDone(row)" @click="voidPlan(row)">
              作废
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 维保工单 -->
    <section class="panel">
      <div class="panel-head">
        <h3>维保工单</h3>
        <span class="panel-sub">计划落到执行 · 也可临时派工开单</span>
        <div class="panel-extra">
          <el-button size="small" type="primary" @click="showForm = !showForm">
            {{ showForm ? '收起' : '临时开单' }}
          </el-button>
        </div>
      </div>

      <div v-if="showForm" class="inline-form" style="margin-bottom: 12px">
        <el-input v-model="form.orderNo" size="small" style="width: 140px" placeholder="工单号 MO-2026-00xx" />
        <el-select v-model="form.elevatorId" size="small" style="width: 170px" placeholder="电梯">
          <el-option v-for="e in elevatorOptions" :key="e.id" :label="e.elevatorNo" :value="e.id" />
        </el-select>
        <el-select v-model="form.cycle" size="small" style="width: 100px" placeholder="周期">
          <el-option label="半月" value="HALF_MONTH" />
          <el-option label="月度" value="MONTH" />
          <el-option label="季度" value="QUARTER" />
          <el-option label="半年" value="HALF_YEAR" />
          <el-option label="年度" value="YEAR" />
        </el-select>
        <el-input v-model="form.team" size="small" style="width: 100px" placeholder="班组" />
        <el-input v-model="form.worker" size="small" style="width: 90px" placeholder="维保员" />
        <el-button size="small" type="primary" @click="createOrder">开单</el-button>
      </div>

      <el-table :data="orders" size="small" border stripe :row-class-name="orderRowClass">
        <el-table-column prop="orderNo" label="工单号" width="120">
          <template #default="{ row }">
            <b :style="{ color: row.orderKind === 'EMERGENCY' ? '#c62828' : '' }">{{ row.orderNo }}</b>
            <el-tag v-if="row.orderKind === 'EMERGENCY'" size="small" type="danger" effect="plain" style="margin-left: 4px">抢修</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="planNo" label="来源计划" width="120" />
        <el-table-column label="关联报修" width="120">
          <template #default="{ row }">{{ row.faultNo || '—' }}</template>
        </el-table-column>
        <el-table-column prop="elevatorNo" label="电梯" width="120" />
        <el-table-column prop="buildingName" label="楼宇" width="130" />
        <el-table-column prop="cycleName" label="周期" width="70" />
        <el-table-column prop="planDate" label="计划执行日" width="110" />
        <el-table-column prop="worker" label="维保员" width="80" />
        <el-table-column label="实际到场" width="150">
          <template #default="{ row }">{{ row.arriveTime ? String(row.arriveTime).replace('T', ' ').slice(0, 16) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="executeResult" label="执行结果" min-width="180" />
        <el-table-column prop="actualDate" label="实际完成日" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210">
          <template #default="{ row }">
            <template v-if="row.orderKind === 'EMERGENCY'">
              <el-tag size="small" type="danger" effect="plain">应急联动</el-tag>
              <el-button size="small" type="warning" plain @click="jumpFault(row)">
                {{ isTerminal(row.status) ? '查看报修' : '去处置' }}
              </el-button>
            </template>
            <template v-else>
              <el-button size="small" type="success" plain :disabled="isDone(row)" @click="executeOrder(row)">
                执行
              </el-button>
              <el-button size="small" type="warning" plain :disabled="isDone(row)" @click="delayOrder(row)">
                延期
              </el-button>
              <el-button size="small" type="info" plain :disabled="isDone(row)" @click="voidOrder(row)">
                作废
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { inject, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { elevatorApi, orderApi, planApi } from '../api'

const es = inject('es')
const nav = useRouter()
const plans = ref([])
const orders = ref([])
const elevatorOptions = ref([])
const showForm = ref(false)
const form = reactive({ orderNo: '', elevatorId: null, cycle: 'MONTH', team: '', worker: '' })

const CYCLE_COLOR = {
  HALF_MONTH: '#ef6c00',
  MONTH: '#ff9800',
  QUARTER: '#8e24aa',
  HALF_YEAR: '#00897b',
  YEAR: '#1565c0'
}

function cycleColor (cycle) {
  return CYCLE_COLOR[cycle] || '#ef6c00'
}

function statusType (status) {
  return {
    PENDING: 'warning',
    DONE: 'success',
    DELAYED: 'danger',
    SUSPENDED: 'danger',
    PROCESSING: 'warning',
    WAIT_ACCEPT: 'primary',
    FIXED: 'success',
    CANCELLED: 'info',
    VOID: 'info'
  }[status] || 'info'
}

function isDone (row) {
  return row.status === 'DONE' || row.status === 'VOID' || row.status === 'FIXED' || row.status === 'CANCELLED'
}

function isPlanDone (row) {
  return isDone(row) || row.status === 'SUSPENDED'
}

function isTerminal (status) {
  return status === 'FIXED' || status === 'CANCELLED'
}

function orderRowClass ({ row }) {
  if (row.orderKind === 'EMERGENCY' && !isTerminal(row.status)) return 'row-emergency'
  if (row.status === 'CANCELLED') return 'row-closed'
  return ''
}

function jumpFault (row) {
  nav.push('/faults')
}

async function load () {
  plans.value = await planApi.list(es.buildingId.value, es.elevatorId.value)
  orders.value = await orderApi.list(es.buildingId.value, es.elevatorId.value)
  elevatorOptions.value = await elevatorApi.list(null, null, null)
}

async function executePlan (row) {
  const { value } = await ElMessageBox.prompt('填写执行结果', '执行计划：' + row.planNo, {
    inputValue: '例行保养合格',
    inputValidator: (v) => (v && v.trim() ? true : '执行结果不能为空')
  })
  await planApi.execute(row.id, { executeResult: value, worker: row.worker })
  await load()
  await es.refreshOverview()
}

async function delayPlan (row) {
  const { value } = await ElMessageBox.prompt('顺延天数', '延期计划：' + row.planNo, { inputValue: '15' })
  await planApi.delay(row.id, Number(value))
  await load()
  await es.refreshOverview()
}

async function voidPlan (row) {
  await ElMessageBox.confirm('确认作废计划 ' + row.planNo + '？', '作废', { type: 'warning' })
  await planApi.void(row.id)
  await load()
  await es.refreshOverview()
}

async function executeOrder (row) {
  const { value } = await ElMessageBox.prompt('填写执行结果', '执行工单：' + row.orderNo, {
    inputValue: '例行保养合格',
    inputValidator: (v) => (v && v.trim() ? true : '执行结果不能为空')
  })
  await orderApi.execute(row.id, { executeResult: value, worker: row.worker })
  await load()
  await es.refreshOverview()
}

async function delayOrder (row) {
  const { value } = await ElMessageBox.prompt('顺延天数', '延期工单：' + row.orderNo, { inputValue: '15' })
  await orderApi.delay(row.id, Number(value))
  await load()
  await es.refreshOverview()
}

async function voidOrder (row) {
  await ElMessageBox.confirm('确认作废工单 ' + row.orderNo + '？', '作废', { type: 'warning' })
  await orderApi.void(row.id)
  await load()
  await es.refreshOverview()
}

async function createOrder () {
  await orderApi.create({
    orderNo: form.orderNo,
    elevatorId: form.elevatorId,
    cycle: form.cycle,
    team: form.team,
    worker: form.worker
  })
  form.orderNo = ''
  await load()
  await es.refreshOverview()
}

onMounted(load)
watch([es.buildingId, es.elevatorId], load)
</script>
