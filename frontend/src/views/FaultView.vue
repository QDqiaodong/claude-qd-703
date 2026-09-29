<template>
  <div>
    <!-- 应急联动处置 -->
    <section class="panel">
      <div class="panel-head">
        <h3>故障报修 · 应急联动</h3>
        <span class="panel-sub">
          困人/停梯自动进入应急态：受理即生成抢修工单、电梯进入「应急维修」、暂停在途计划；
          只能按 待受理→抢修中→待验收→已修复 推进，误报走「已撤销」
        </span>
        <div class="panel-extra">
          <el-button size="small" type="primary" @click="showForm = !showForm">
            {{ showForm ? '收起' : '登记报修' }}
          </el-button>
        </div>
      </div>

      <div v-if="showForm" class="inline-form" style="margin-bottom: 12px">
        <el-input v-model="form.faultNo" size="small" style="width: 140px" placeholder="报修单号 FR-2026-00xx" />
        <el-select v-model="form.elevatorId" size="small" style="width: 160px" placeholder="电梯">
          <el-option v-for="e in elevatorOptions" :key="e.id" :label="e.elevatorNo" :value="e.id" />
        </el-select>
        <el-select v-model="form.faultType" size="small" style="width: 110px" placeholder="故障现象">
          <el-option label="困人（应急）" value="TRAPPED" />
          <el-option label="停梯（应急）" value="STOPPED" />
          <el-option label="异响" value="NOISE" />
          <el-option label="不平层" value="LEVELING" />
          <el-option label="门系统" value="DOOR" />
          <el-option label="其他" value="OTHER" />
        </el-select>
        <el-input v-model="form.responseMinutes" size="small" style="width: 100px" placeholder="响应(分钟)" />
        <el-input v-model="form.arriveMinutes" size="small" style="width: 100px" placeholder="到达(分钟)" />
        <el-input v-model="form.reporter" size="small" style="width: 110px" placeholder="报修人" />
        <el-button size="small" type="primary" @click="createFault">登记</el-button>
        <span style="color: #909399; font-size: 12px">困人/停梯登记即为高风险应急项</span>
      </div>

      <!-- 应急故障 -->
      <div style="margin: 6px 0 8px; font-weight: 600; color: #c62828">🚨 应急联动（困人 / 停梯）</div>
      <el-table :data="emergencyRows" size="small" border stripe :row-class-name="emergencyRowClass">
        <el-table-column prop="faultNo" label="报修单号" width="120" />
        <el-table-column label="风险" width="80">
          <template #default="{ row }">
            <el-tag size="small" type="danger" effect="dark">高风险</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="elevatorNo" label="电梯" width="115" />
        <el-table-column prop="buildingName" label="楼宇" width="130" />
        <el-table-column prop="faultTypeName" label="故障现象" width="80" />
        <el-table-column label="处置阶段" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="faultStatusType(row.status)">{{ row.stage || row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assignee" label="当前负责人" width="95">
          <template #default="{ row }">{{ row.assignee || '—' }}</template>
        </el-table-column>
        <el-table-column prop="reportTime" label="报修时间" width="150" />
        <el-table-column label="受理时间" width="150">
          <template #default="{ row }">{{ row.acceptTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="抢修工单 / 到场" width="200">
          <template #default="{ row }">
            <div v-if="row.orderNo">
              <el-button link type="primary" @click="openOrder(row)">{{ row.orderNo }}</el-button>
              <el-tag size="small" :type="faultStatusType(row.status)" effect="plain">{{ row.statusName }}</el-tag>
            </div>
            <span v-else style="color: #909399">受理后自动生成</span>
            <div v-if="row.arriveTime" style="font-size: 12px; color: #606266">到场 {{ formatTime(row.arriveTime) }}</div>
            <div v-else-if="row.orderNo" style="font-size: 12px; color: #e6a23c">待填实际到场</div>
          </template>
        </el-table-column>
        <el-table-column prop="handleResult" label="处理结果" min-width="170">
          <template #default="{ row }">
            <span>{{ row.handleResult || '—' }}</span>
            <div v-if="row.cancelReason" style="color: #909399; font-size: 12px">撤销原因：{{ row.cancelReason }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain :disabled="row.status !== 'PENDING'" @click="acceptFault(row)">
              受理
            </el-button>
            <el-button size="small" type="warning" plain :disabled="row.status !== 'PROCESSING'" @click="processFault(row)">
              处置完成
            </el-button>
            <el-button size="small" type="success" plain :disabled="row.status !== 'WAIT_ACCEPT'" @click="closeFault(row)">
              验收闭环
            </el-button>
            <el-button size="small" type="info" plain :disabled="isTerminal(row.status)" @click="cancelFault(row)">
              误报撤销
            </el-button>
            <el-button size="small" link type="primary" @click="showLogs(row)">记录</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 普通故障：保留原有报修流程 -->
      <div style="margin: 16px 0 8px; font-weight: 600; color: #303133">🔧 普通故障（异响 / 不平层 / 门系统等，走原报修流程）</div>
      <el-table :data="normalRows" size="small" border stripe>
        <el-table-column prop="faultNo" label="报修单号" width="120" />
        <el-table-column prop="elevatorNo" label="电梯" width="120" />
        <el-table-column prop="buildingName" label="楼宇" width="130" />
        <el-table-column prop="faultTypeName" label="故障现象" width="90" />
        <el-table-column prop="reportTime" label="报修时间" width="150" />
        <el-table-column label="响应 / 到达" width="170">
          <template #default="{ row }">
            <span :style="{ color: row.arriveOverdue ? '#c62828' : '' }">
              响应 {{ row.responseMinutes }} 分钟 / 到达 {{ row.arriveMinutes }} 分钟
              <b v-if="row.arriveOverdue"> · 超红线</b>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="handleResult" label="处理结果" min-width="180" />
        <el-table-column prop="reporter" label="报修人" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="faultStatusType(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" type="success" plain :disabled="isTerminal(row.status)" @click="advance(row)">
              {{ row.status === 'PENDING' ? '受理' : '推进' }}
            </el-button>
            <el-button size="small" type="danger" plain :disabled="isTerminal(row.status)" @click="fix(row)">
              闭环
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 处置操作记录 -->
    <el-drawer v-model="logDrawer" :title="'处置操作记录 · ' + (current.faultNo || '')" size="42%">
      <el-timeline v-if="logs.length">
        <el-timeline-item
          v-for="l in logs"
          :key="l.id"
          :timestamp="formatTime(l.createdAt)"
          :type="logColor(l.action)"
        >
          <b>{{ l.actionName }}</b>
          <span style="color: #909399; margin-left: 8px">{{ l.operator }}</span>
          <div v-if="l.fromStatus || l.toStatus" style="font-size: 12px; color: #606266">
            {{ statusName(l.fromStatus) }} → {{ statusName(l.toStatus) }}
          </div>
          <div style="margin-top: 2px">{{ l.detail }}</div>
          <div v-if="l.orderNo" style="font-size: 12px">抢修工单：<b>{{ l.orderNo }}</b></div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无操作记录" />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { elevatorApi, faultApi, orderApi } from '../api'

const es = inject('es')
const faults = ref([])
const elevatorOptions = ref([])
const showForm = ref(false)
const logDrawer = ref(false)
const logs = ref([])
const current = reactive({ id: null, faultNo: '' })
const form = reactive({
  faultNo: '', elevatorId: null, faultType: 'TRAPPED',
  responseMinutes: '', arriveMinutes: '', reporter: ''
})

const emergencyRows = computed(() => faults.value.filter((r) => r.emergency))
const normalRows = computed(() => faults.value.filter((r) => !r.emergency))

function emergencyRowClass ({ row }) {
  if (row.status === 'FIXED' || row.status === 'CANCELLED') return 'row-closed'
  return row.trapped ? 'row-trapped' : 'row-emergency'
}

function faultStatusType (status) {
  return {
    PENDING: 'danger',
    PROCESSING: 'warning',
    WAIT_ACCEPT: 'primary',
    FIXED: 'success',
    CANCELLED: 'info'
  }[status] || 'info'
}

function isTerminal (status) {
  return status === 'FIXED' || status === 'CANCELLED'
}

function statusName (s) {
  return { PENDING: '待处理', PROCESSING: '处理中', WAIT_ACCEPT: '待验收', FIXED: '已修复', CANCELLED: '已撤销' }[s] || s || '—'
}

function formatTime (t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
}

function nowInput () {
  const d = new Date()
  d.setMinutes(d.getMinutes() - 5)
  return d.toISOString().slice(0, 16)
}

function logColor (action) {
  return { CREATE: 'info', ACCEPT: 'primary', ADVANCE: 'warning', CANCEL: 'info', CLOSE: 'success', REJECT: 'danger' }[action]
}

async function load () {
  faults.value = await faultApi.list(es.buildingId.value, es.elevatorId.value)
  elevatorOptions.value = await elevatorApi.list(null, null, null)
}

async function createFault () {
  if (!form.elevatorId) {
    ElMessage.warning('请选择电梯')
    return
  }
  await faultApi.create({
    faultNo: form.faultNo,
    elevatorId: form.elevatorId,
    faultType: form.faultType,
    responseMinutes: Number(form.responseMinutes || 0),
    arriveMinutes: Number(form.arriveMinutes || 0),
    reporter: form.reporter
  })
  form.faultNo = ''
  form.responseMinutes = ''
  form.arriveMinutes = ''
  ElMessage.success('报修单已登记')
  await load()
  await es.refreshOverview()
}

async function acceptFault (row) {
  const { value } = await ElMessageBox.prompt('受理人 / 当前负责人', '受理应急报修：' + row.faultNo, {
    inputValue: '应急抢修组',
    inputValidator: (v) => (v && v.trim() ? true : '请填写负责人')
  })
  await faultApi.accept(row.id, { assignee: value, team: '应急抢修组', worker: value, operator: value })
  ElMessage.success('已受理：抢修工单已生成，电梯进入应急维修')
  await load()
  await es.refreshOverview()
  await es.reloadTree()
}

async function processFault (row) {
  const { value: result } = await ElMessageBox.prompt('处理结果（抢修完成情况）', '处置：' + row.faultNo, {
    inputValue: '现场抢修完成，故障排除',
    inputValidator: (v) => (v && v.trim() ? true : '处理结果不能为空')
  })
  const { value: arrive } = await ElMessageBox.prompt('实际到场时间', '实际到场信息：' + row.faultNo, {
    inputValue: nowInput(),
    inputValidator: (v) => (v && v.trim() ? true : '请填写实际到场时间')
  })
  await faultApi.process(row.id, {
    handleResult: result,
    arriveTime: arrive.length === 16 ? arrive + ':00' : arrive,
    operator: row.assignee || '值班员'
  })
  ElMessage.success('处置结果已提交，进入待验收')
  await load()
}

async function closeFault (row) {
  await ElMessageBox.confirm(
    '闭环前确认：① 已填写处理结果；② 已填写实际到场信息；③ 电梯已恢复运行。确认闭环？',
    '验收闭环：' + row.faultNo, { type: 'warning', confirmButtonText: '确认恢复运行并闭环', cancelButtonText: '取消' }
  )
  await faultApi.close(row.id, { resumed: true, operator: '验收员' })
  ElMessage.success('已闭环：电梯占用解除，被暂停计划已恢复')
  await load()
  await es.refreshOverview()
  await es.reloadTree()
}

async function cancelFault (row) {
  const { value } = await ElMessageBox.prompt('判定误报，必须填写撤销原因', '误报撤销：' + row.faultNo, {
    inputPlaceholder: '例如：业主误报，现场复核电梯运行正常',
    inputType: 'textarea',
    inputValidator: (v) => (v && v.trim() ? true : '撤销原因不能为空')
  })
  await faultApi.cancel(row.id, { cancelReason: value, operator: '值班员' })
  ElMessage.success('已按误报撤销，进入独立终态「已撤销」')
  await load()
  await es.refreshOverview()
  await es.reloadTree()
}

async function showLogs (row) {
  current.id = row.id
  current.faultNo = row.faultNo
  logs.value = await faultApi.logs(row.id)
  logDrawer.value = true
}

/** 从故障行直接查看关联工单 */
async function openOrder (row) {
  const all = await orderApi.list(row.buildingId, row.elevatorId)
  const order = all.find((o) => o.id === row.orderId || o.orderNo === row.orderNo)
  if (!order) {
    ElMessage.info('未在当前楼宇筛选下找到工单 ' + row.orderNo)
    return
  }
  ElMessageBox.alert(
    `工单号：${order.orderNo}（${order.orderKindName}）
电梯：${order.elevatorNo}　楼宇：${order.buildingName}
关联报修：${order.faultNo || '—'}
状态：${order.statusName}
负责人：${order.worker || order.team || '—'}
实际到场：${formatTime(order.arriveTime)}
处理结果：${order.executeResult || '（抢修中，待填报）'}`,
    '抢修工单详情',
    { confirmButtonText: '知道了' }
  )
}

async function advance (row) {
  await faultApi.advance(row.id)
  await load()
  await es.refreshOverview()
}

async function fix (row) {
  const { value } = await ElMessageBox.prompt('处理结果', '闭环报修：' + row.faultNo, {
    inputValue: '现场处理完毕，恢复运行',
    inputValidator: (v) => (v && v.trim() ? true : '处理结果不能为空')
  })
  await faultApi.fix(row.id, value)
  await load()
  await es.refreshOverview()
}

onMounted(load)
watch([es.buildingId, es.elevatorId], load)
</script>
