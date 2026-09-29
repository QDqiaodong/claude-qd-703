<template>
  <div>
    <section class="panel">
      <div class="panel-head">
        <h3>电梯台账</h3>
        <span class="panel-sub">
          当前过滤器：{{ es.buildingId.value ? 'buildingFilter（按楼宇收口，停用梯可见）' : 'statusFilter（停用梯默认隐藏）' }}
        </span>
        <div class="panel-extra">
          <el-button size="small" type="primary" @click="showForm = !showForm">
            {{ showForm ? '收起' : '新增电梯' }}
          </el-button>
        </div>
      </div>

      <div v-if="showForm" class="inline-form" style="margin-bottom: 12px">
        <el-input v-model="form.elevatorNo" size="small" style="width: 140px" placeholder="电梯编号" />
        <el-select v-model="form.unitId" size="small" style="width: 180px" placeholder="所属单元">
          <el-option v-for="u in unitOptions" :key="u.id" :label="u.unitNo + ' · ' + u.buildingName" :value="u.id" />
        </el-select>
        <el-input v-model="form.brand" size="small" style="width: 90px" placeholder="品牌" />
        <el-input v-model="form.model" size="small" style="width: 130px" placeholder="型号" />
        <el-input v-model="form.ratedLoad" size="small" style="width: 100px" placeholder="额定载重 kg" />
        <el-input v-model="form.ratedSpeed" size="small" style="width: 100px" placeholder="额定速度 m/s" />
        <el-input v-model="form.floorStation" size="small" style="width: 90px" placeholder="层站数" />
        <el-input v-model="form.installDate" size="small" style="width: 130px" placeholder="安装日期 2020-01-01" />
        <el-input v-model="form.nextInspectDate" size="small" style="width: 130px" placeholder="下次年检日" />
        <el-input v-model="form.maintVendor" size="small" style="width: 180px" placeholder="维保单位" />
        <el-button size="small" type="primary" @click="createElevator">建档</el-button>
      </div>

      <el-table
        :data="elevators"
        size="small"
        border
        stripe
        :row-class-name="rowClass"
        @row-click="pickRow"
      >
        <el-table-column prop="elevatorNo" label="电梯编号" width="120" />
        <el-table-column prop="buildingName" label="楼宇" width="130" />
        <el-table-column prop="unitNo" label="单元" width="110" />
        <el-table-column prop="brand" label="品牌" width="70" />
        <el-table-column prop="model" label="型号" width="130" />
        <el-table-column prop="ratedLoad" label="载重(kg)" width="80" align="right" />
        <el-table-column prop="ratedSpeed" label="速度(m/s)" width="90" align="right" />
        <el-table-column prop="floorStation" label="层站" width="65" align="right" />
        <el-table-column prop="installDate" label="安装日期" width="105" />
        <el-table-column prop="lastInspectDate" label="上次年检" width="105" />
        <el-table-column label="下次年检" width="140">
          <template #default="{ row }">
            <span :style="{ color: row.inspectDueDays != null && row.inspectDueDays < 60 ? '#c62828' : '' }">
              {{ row.nextInspectDate }}
              <span v-if="row.inspectDueDays != null" class="muted">
                （{{ row.inspectDueDays }} 天）
              </span>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="maintVendor" label="维保单位" min-width="170" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ row.statusName }}</el-tag>
            <div v-if="row.emergencyOccupied" style="font-size: 12px; color: #c62828; margin-top: 2px">
              ⛔ {{ row.emergencyFaultNo }} / {{ row.emergencyOrderNo }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="currentFloor" label="停靠层" width="70" align="right" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-select
              :model-value="row.status"
              size="small"
              style="width: 130px"
              :disabled="row.emergencyOccupied"
              @change="(v) => changeStatus(row, v)"
            >
              <el-option label="运行" value="RUNNING" />
              <el-option label="停用" value="OUT_OF_SERVICE" />
              <el-option label="维修" value="REPAIRING" />
              <el-option label="年检中" value="INSPECTING" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { inject, onMounted, reactive, ref, watch } from 'vue'
import { elevatorApi, unitApi } from '../api'

const es = inject('es')
const elevators = ref([])
const unitOptions = ref([])
const showForm = ref(false)
const form = reactive({
  elevatorNo: '', unitId: null, brand: '', model: '', ratedLoad: '', ratedSpeed: '',
  floorStation: '', installDate: '', nextInspectDate: '', maintVendor: ''
})

function statusType (status) {
  return {
    RUNNING: 'success',
    OUT_OF_SERVICE: 'info',
    REPAIRING: 'warning',
    INSPECTING: 'primary',
    EMERGENCY_REPAIR: 'danger'
  }[status] || 'info'
}

function rowClass ({ row }) {
  if (row.emergencyOccupied) return 'row-emergency'
  return row.status === 'OUT_OF_SERVICE' ? 'row-out' : ''
}

function pickRow (row) {
  es.elevatorId.value = row.id
  es.unitId.value = row.unitId
  es.buildingId.value = row.buildingId
}

async function load () {
  elevators.value = await elevatorApi.list(es.buildingId.value, es.unitId.value, null)
  unitOptions.value = await unitApi.list(null)
}

async function createElevator () {
  await elevatorApi.create({
    elevatorNo: form.elevatorNo,
    unitId: form.unitId,
    brand: form.brand,
    model: form.model,
    ratedLoad: Number(form.ratedLoad),
    ratedSpeed: Number(form.ratedSpeed),
    floorStation: Number(form.floorStation),
    installDate: form.installDate || null,
    nextInspectDate: form.nextInspectDate || null,
    maintVendor: form.maintVendor
  })
  Object.assign(form, {
    elevatorNo: '', brand: '', model: '', ratedLoad: '', ratedSpeed: '',
    floorStation: '', installDate: '', nextInspectDate: ''
  })
  await load()
  await es.reloadTree()
  await es.refreshOverview()
}

async function changeStatus (row, status) {
  await elevatorApi.setStatus(row.id, status)
  await load()
  await es.reloadTree()
  await es.refreshOverview()
}

onMounted(load)
watch([es.buildingId, es.unitId, es.elevatorId], load)
</script>
