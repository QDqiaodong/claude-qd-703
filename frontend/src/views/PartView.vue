<template>
  <div>
    <section class="panel">
      <div class="panel-head">
        <h3>配件更换</h3>
        <span class="panel-sub">金额 = 数量 × 单价 · 可关联维保工单或故障报修单</span>
        <div class="panel-extra">
          <el-button size="small" type="primary" @click="showForm = !showForm">
            {{ showForm ? '收起' : '登记更换' }}
          </el-button>
        </div>
      </div>

      <div v-if="showForm" class="inline-form" style="margin-bottom: 12px">
        <el-select v-model="form.elevatorId" size="small" style="width: 160px" placeholder="电梯">
          <el-option v-for="e in elevatorOptions" :key="e.id" :label="e.elevatorNo" :value="e.id" />
        </el-select>
        <el-input v-model="form.partName" size="small" style="width: 130px" placeholder="配件名称" />
        <el-input v-model="form.spec" size="small" style="width: 120px" placeholder="规格型号" />
        <el-input v-model="form.quantity" size="small" style="width: 80px" placeholder="数量" />
        <el-input v-model="form.unitPrice" size="small" style="width: 90px" placeholder="单价" />
        <el-input v-model="form.operator" size="small" style="width: 90px" placeholder="更换人" />
        <el-select v-model="form.refType" size="small" style="width: 110px" placeholder="关联单据">
          <el-option label="不关联" value="" />
          <el-option label="维保工单" value="ORDER" />
          <el-option label="故障报修" value="FAULT" />
        </el-select>
        <el-input v-model="form.refId" size="small" style="width: 90px" placeholder="单据 id" />
        <el-input v-model="form.supplier" size="small" style="width: 160px" placeholder="供应商" />
        <el-button size="small" type="primary" @click="createPart">登记</el-button>
      </div>

      <el-table :data="parts" size="small" border stripe>
        <el-table-column prop="partName" label="配件名称" width="130" />
        <el-table-column prop="spec" label="规格型号" width="130" />
        <el-table-column prop="quantity" label="数量" width="70" align="right" />
        <el-table-column label="单价" width="90" align="right">
          <template #default="{ row }">¥ {{ row.unitPrice }}</template>
        </el-table-column>
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }">
            <b style="color: #b8500a">¥ {{ row.amount }}</b>
          </template>
        </el-table-column>
        <el-table-column prop="replaceDate" label="更换日期" width="110" />
        <el-table-column prop="operator" label="更换人" width="80" />
        <el-table-column label="关联单号" width="150">
          <template #default="{ row }">
            <span v-if="row.refNo">
              <el-tag size="small" :type="row.refType === 'ORDER' ? 'success' : 'danger'">
                {{ row.refTypeName }}
              </el-tag>
              {{ row.refNo }}
            </span>
            <span v-else class="muted">未关联</span>
          </template>
        </el-table-column>
        <el-table-column prop="supplier" label="供应商" min-width="160" />
        <el-table-column prop="elevatorNo" label="电梯" width="120" />
        <el-table-column prop="buildingName" label="楼宇" width="130" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'NORMAL' ? 'success' : 'info'">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 底部金额汇总 -->
      <div class="sum-bar">
        <div class="sum-item">
          <span>记录条数</span><b>{{ summary.recordCount || 0 }}</b>
        </div>
        <div class="sum-item">
          <span>配件总数量</span><b>{{ summary.totalQuantity || 0 }}</b>
        </div>
        <div class="sum-item">
          <span>金额合计</span><b>¥ {{ summary.totalAmount || 0 }}</b>
        </div>
        <div class="sum-item" style="margin-left: auto">
          <span>按供应商：</span>
          <el-tag
            v-for="s in summary.bySupplier || []"
            :key="s.supplier"
            size="small"
            type="warning"
            style="margin-right: 6px"
          >
            {{ s.supplier }} ¥ {{ s.amount }}
          </el-tag>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { inject, onMounted, reactive, ref, watch } from 'vue'
import { elevatorApi, partApi } from '../api'

const es = inject('es')
const parts = ref([])
const summary = ref({})
const elevatorOptions = ref([])
const showForm = ref(false)
const form = reactive({
  elevatorId: null, partName: '', spec: '', quantity: '', unitPrice: '',
  operator: '', refType: '', refId: '', supplier: ''
})

async function load () {
  parts.value = await partApi.list(es.buildingId.value, es.elevatorId.value)
  summary.value = await partApi.summary(es.buildingId.value)
  elevatorOptions.value = await elevatorApi.list(null, null, null)
}

async function createPart () {
  await partApi.create({
    elevatorId: form.elevatorId,
    partName: form.partName,
    spec: form.spec,
    quantity: Number(form.quantity),
    unitPrice: Number(form.unitPrice),
    operator: form.operator,
    refType: form.refType || null,
    refId: form.refId ? Number(form.refId) : null,
    supplier: form.supplier
  })
  Object.assign(form, { partName: '', spec: '', quantity: '', unitPrice: '', refId: '' })
  await load()
  await es.refreshOverview()
}

onMounted(load)
watch([es.buildingId, es.elevatorId], load)
</script>
