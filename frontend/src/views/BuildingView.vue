<template>
  <div>
    <!-- 楼宇 -->
    <section class="panel">
      <div class="panel-head">
        <h3>楼宇档案</h3>
        <span class="panel-sub">
          当前过滤器：{{ es.buildingId.value ? 'buildingFilter（按楼宇收口）' : 'statusFilter（只留在管楼宇）' }}
        </span>
        <div class="panel-extra">
          <el-button size="small" type="primary" @click="showBuildingForm = !showBuildingForm">
            {{ showBuildingForm ? '收起' : '新增楼宇' }}
          </el-button>
        </div>
      </div>

      <div v-if="showBuildingForm" class="inline-form" style="margin-bottom: 12px">
        <el-input v-model="bForm.buildingNo" size="small" style="width: 120px" placeholder="楼宇编号 BL-0xx" />
        <el-input v-model="bForm.name" size="small" style="width: 150px" placeholder="楼宇名称" />
        <el-input v-model="bForm.address" size="small" style="width: 210px" placeholder="地址" />
        <el-input v-model="bForm.floorCount" size="small" style="width: 90px" placeholder="层数" />
        <el-input v-model="bForm.buildYear" size="small" style="width: 100px" placeholder="建成年代" />
        <el-input v-model="bForm.propertyContact" size="small" style="width: 110px" placeholder="物业联系人" />
        <el-input v-model="bForm.propertyPhone" size="small" style="width: 130px" placeholder="联系电话" />
        <el-button size="small" type="primary" @click="createBuilding">建档</el-button>
      </div>

      <el-table :data="buildings" size="small" border stripe>
        <el-table-column prop="buildingNo" label="楼宇编号" width="100" />
        <el-table-column prop="name" label="楼宇名称" width="150" />
        <el-table-column prop="address" label="地址" min-width="200" />
        <el-table-column prop="floorCount" label="层数" width="70" align="right" />
        <el-table-column prop="buildYear" label="建成年代" width="90" align="right" />
        <el-table-column prop="propertyContact" label="物业联系人" width="100" />
        <el-table-column prop="propertyPhone" label="联系电话" width="120" />
        <el-table-column prop="unitCount" label="单元数" width="70" align="right" />
        <el-table-column prop="elevatorCount" label="电梯数" width="70" align="right" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button
              size="small"
              :type="row.status === 'ACTIVE' ? 'warning' : 'success'"
              plain
              @click="toggleBuilding(row)"
            >
              {{ row.status === 'ACTIVE' ? '撤场' : '恢复在管' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 单元 -->
    <section class="panel">
      <div class="panel-head">
        <h3>楼栋单元</h3>
        <span class="panel-sub">单元层数不得超过楼宇层数；已撤场楼宇不能新增单元</span>
        <div class="panel-extra">
          <el-button size="small" type="primary" @click="showUnitForm = !showUnitForm">
            {{ showUnitForm ? '收起' : '新增单元' }}
          </el-button>
        </div>
      </div>

      <div v-if="showUnitForm" class="inline-form" style="margin-bottom: 12px">
        <el-input v-model="uForm.unitNo" size="small" style="width: 140px" placeholder="单元编号 U-BL0xx-0x" />
        <el-select v-model="uForm.buildingId" size="small" style="width: 180px" placeholder="所属楼宇">
          <el-option v-for="b in buildings" :key="b.id" :label="b.name" :value="b.id" />
        </el-select>
        <el-input v-model="uForm.floorCount" size="small" style="width: 90px" placeholder="单元层数" />
        <el-input v-model="uForm.elevatorCount" size="small" style="width: 90px" placeholder="电梯数" />
        <el-input v-model="uForm.note" size="small" style="width: 180px" placeholder="备注" />
        <el-button size="small" type="primary" @click="createUnit">建档</el-button>
      </div>

      <el-table :data="units" size="small" border stripe>
        <el-table-column prop="unitNo" label="单元编号" width="130" />
        <el-table-column prop="buildingName" label="所属楼宇" width="150" />
        <el-table-column prop="floorCount" label="层数" width="70" align="right" />
        <el-table-column prop="elevatorCount" label="登记电梯数" width="100" align="right" />
        <el-table-column prop="realElevatorCount" label="台账实挂" width="90" align="right" />
        <el-table-column prop="note" label="备注" min-width="160" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button
              size="small"
              :type="row.status === 'ACTIVE' ? 'warning' : 'success'"
              plain
              @click="toggleUnit(row)"
            >
              {{ row.status === 'ACTIVE' ? '撤场' : '恢复在管' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { inject, onMounted, reactive, ref, watch } from 'vue'
import { buildingApi, unitApi } from '../api'

const es = inject('es')
const buildings = ref([])
const units = ref([])
const showBuildingForm = ref(false)
const showUnitForm = ref(false)

const bForm = reactive({
  buildingNo: '', name: '', address: '', floorCount: '', buildYear: '',
  propertyContact: '', propertyPhone: ''
})
const uForm = reactive({ unitNo: '', buildingId: null, floorCount: '', elevatorCount: '', note: '' })

async function load () {
  buildings.value = await buildingApi.list(es.buildingId.value)
  units.value = await unitApi.list(es.buildingId.value)
}

async function createBuilding () {
  await buildingApi.create({
    buildingNo: bForm.buildingNo,
    name: bForm.name,
    address: bForm.address,
    floorCount: Number(bForm.floorCount),
    buildYear: Number(bForm.buildYear),
    propertyContact: bForm.propertyContact,
    propertyPhone: bForm.propertyPhone
  })
  Object.assign(bForm, {
    buildingNo: '', name: '', address: '', floorCount: '', buildYear: '',
    propertyContact: '', propertyPhone: ''
  })
  await load()
  await es.reloadTree()
}

async function createUnit () {
  await unitApi.create({
    unitNo: uForm.unitNo,
    buildingId: uForm.buildingId,
    floorCount: Number(uForm.floorCount),
    elevatorCount: Number(uForm.elevatorCount),
    note: uForm.note
  })
  Object.assign(uForm, { unitNo: '', floorCount: '', elevatorCount: '', note: '' })
  await load()
  await es.reloadTree()
}

async function toggleBuilding (row) {
  await buildingApi.setStatus(row.id, row.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE')
  await load()
  await es.reloadTree()
}

async function toggleUnit (row) {
  await unitApi.setStatus(row.id, row.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE')
  await load()
  await es.reloadTree()
}

onMounted(load)
watch([es.buildingId, es.unitId], load)
</script>
