<template>
  <div>
    <section class="panel">
      <div class="panel-head">
        <h3>故障报修</h3>
        <span class="panel-sub">
          困人整行标红并显示「响应 xx 分钟 / 到达 xx 分钟」· 到达超 30 分钟为红线
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
          <el-option label="困人" value="TRAPPED" />
          <el-option label="异响" value="NOISE" />
          <el-option label="不平层" value="LEVELING" />
          <el-option label="停梯" value="STOPPED" />
          <el-option label="门系统" value="DOOR" />
          <el-option label="其他" value="OTHER" />
        </el-select>
        <el-input v-model="form.responseMinutes" size="small" style="width: 100px" placeholder="响应(分钟)" />
        <el-input v-model="form.arriveMinutes" size="small" style="width: 100px" placeholder="到达(分钟)" />
        <el-input v-model="form.reporter" size="small" style="width: 110px" placeholder="报修人" />
        <el-button size="small" type="primary" @click="createFault">登记</el-button>
      </div>

      <el-table :data="faults" size="small" border stripe :row-class-name="rowClass">
        <el-table-column prop="faultNo" label="报修单号" width="120" />
        <el-table-column prop="elevatorNo" label="电梯" width="120" />
        <el-table-column prop="buildingName" label="楼宇" width="130" />
        <el-table-column label="故障现象" width="100">
          <template #default="{ row }">
            <b v-if="row.trapped" style="color: #c62828">⚠ {{ row.faultTypeName }}</b>
            <span v-else>{{ row.faultTypeName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reportTime" label="报修时间" width="150" />
        <el-table-column label="响应 / 到达" width="160">
          <template #default="{ row }">
            <span :style="{ color: row.arriveOverdue ? '#c62828' : '' }">
              响应 {{ row.responseMinutes }} 分钟 / 到达 {{ row.arriveMinutes }} 分钟
              <b v-if="row.arriveOverdue"> · 超红线</b>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="fixTime" label="修复时间" width="150" />
        <el-table-column prop="handleResult" label="处理结果" min-width="180" />
        <el-table-column prop="reporter" label="报修人" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button
              size="small"
              type="success"
              plain
              :disabled="row.status === 'FIXED'"
              @click="advance(row)"
            >
              {{ row.status === 'PENDING' ? '受理' : '推进' }}
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :disabled="row.status === 'FIXED'"
              @click="fix(row)"
            >
              闭环
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { inject, onMounted, reactive, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { elevatorApi, faultApi } from '../api'

const es = inject('es')
const faults = ref([])
const elevatorOptions = ref([])
const showForm = ref(false)
const form = reactive({
  faultNo: '', elevatorId: null, faultType: 'TRAPPED',
  responseMinutes: '', arriveMinutes: '', reporter: ''
})

function rowClass ({ row }) {
  return row.trapped ? 'row-trapped' : ''
}

function statusType (status) {
  return { PENDING: 'danger', PROCESSING: 'warning', FIXED: 'success' }[status] || 'info'
}

async function load () {
  faults.value = await faultApi.list(es.buildingId.value, es.elevatorId.value)
  elevatorOptions.value = await elevatorApi.list(null, null, null)
}

async function createFault () {
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
  await load()
  await es.refreshOverview()
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
