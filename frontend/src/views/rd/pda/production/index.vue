<template>
  <main class="production-pda">
    <header class="pda-header">
      <h1>{{ selectedId ? '生产录入' : '选择生产任务' }}</h1>
      <el-tooltip :content="selectedId ? '返回任务列表' : '刷新任务列表'">
        <el-button
          circle
          :aria-label="selectedId ? '返回任务列表' : '刷新任务列表'"
          :disabled="submitting || !!pending || actingId !== undefined"
          @click="selectedId ? backToTasks() : loadTasks()"
        >
          <Icon :icon="selectedId ? 'lucide:arrow-left' : 'lucide:refresh-cw'" />
        </el-button>
      </el-tooltip>
    </header>
    <el-result v-if="!canQuery" icon="warning" title="无生产任务查询权限" />
    <template v-else>
      <el-alert v-if="cacheError" :title="cacheError" type="error" :closable="false" show-icon />
      <section v-if="pending" class="pending-band" aria-label="待重试生产记录">
        <el-alert
          :title="submissionError || '有一笔提交结果待核定的生产记录'"
          type="warning"
          :closable="false"
          show-icon
        />
        <dl class="summary-grid">
          <div class="wide-field"
            ><dt>绑定任务</dt><dd>{{ pending.taskNo }}</dd></div
          >
          <div class="wide-field"
            ><dt>客户</dt><dd>{{ pending.payload.customerName }}</dd></div
          >
          <div
            ><dt>业务生产日期</dt><dd>{{ pending.payload.productionDate }}</dd></div
          >
          <div
            ><dt>班次</dt><dd>{{ pending.payload.shiftCode }}</dd></div
          >
          <div
            ><dt>型号</dt><dd>{{ pending.payload.productModel }}</dd></div
          >
          <div
            ><dt>炉号 / 炉层</dt
            ><dd>{{ pending.payload.furnaceNo }} / {{ pending.payload.furnaceLayer }}</dd></div
          >
          <div
            ><dt>包数</dt><dd>{{ pending.payload.outputQty }}</dd></div
          >
          <div
            ><dt>实称单包 kg</dt><dd>{{ pending.payload.packageWeight }}</dd></div
          >
          <div
            ><dt>出炉日期</dt><dd>{{ pending.payload.furnaceOutDate || '-' }}</dd></div
          >
          <div
            ><dt>备注</dt><dd>{{ pending.payload.remark || '-' }}</dd></div
          >
          <div class="wide-field"
            ><dt>请求号</dt><dd class="request-id">{{ pending.payload.clientRequestId }}</dd></div
          >
        </dl>
        <div class="pda-actions">
          <el-button v-if="canCreate" type="primary" :loading="submitting" @click="submitDraft">
            <Icon icon="lucide:rotate-cw" />重试原请求
          </el-button>
          <el-button :disabled="submitting" @click="releasePending">核对后解除绑定</el-button>
        </div>
      </section>
      <el-button v-else-if="cacheError" class="cache-action" @click="releasePending"
        >核对后清理本机记录</el-button
      >

      <section v-if="!selectedId" class="task-selection" aria-label="生产任务列表">
        <el-radio-group v-model="taskStatus" class="status-switch" @change="searchTasks">
          <el-radio-button label="登记中" value="ACTIVE" />
          <el-radio-button label="已结束" value="CLOSED" />
          <el-radio-button label="已作废" value="CANCELLED" />
        </el-radio-group>
        <el-input
          v-model="taskKeyword"
          clearable
          placeholder="任务号"
          aria-label="按任务号查找"
          @keyup.enter="searchTasks"
          @clear="searchTasks"
        >
          <template #append>
            <el-button aria-label="查询任务" @click="searchTasks"
              ><Icon icon="lucide:search"
            /></el-button>
          </template>
        </el-input>
        <el-skeleton v-if="taskLoading" :rows="5" animated />
        <div v-else-if="taskError" class="error-state" role="alert">
          <p>{{ taskError }}</p>
          <el-button @click="loadTasks"><Icon icon="lucide:refresh-cw" />重试</el-button>
        </div>
        <el-empty v-else-if="!tasks.length" :image-size="72" description="暂无符合条件的生产任务" />
        <div v-else class="task-list">
          <button
            v-for="item in tasks"
            :key="item.id"
            type="button"
            class="task-pick"
            @click="openTask(item.id)"
          >
            <div class="item-heading">
              <strong>{{ item.taskNo }}</strong>
              <Icon icon="lucide:chevron-right" />
            </div>
            <span class="task-customer">{{ item.customerName }}</span>
            <div class="task-secondary"
              ><span>{{ item.productionDate }}</span
              ><span>班次 {{ item.shiftCode }}</span></div
            >
            <span v-if="item.managerName" class="task-manager">班长 {{ item.managerName }}</span>
          </button>
        </div>
        <el-pagination
          v-if="taskTotal > 10"
          v-model:current-page="taskPage"
          :page-size="10"
          :total="taskTotal"
          :pager-count="5"
          layout="prev, pager, next"
          small
          @current-change="loadTasks"
        />
      </section>

      <template v-else>
        <el-skeleton v-if="detailLoading" :rows="5" animated />
        <div v-else-if="detailError" class="error-state" role="alert">
          <p>{{ detailError }}</p>
          <el-button @click="loadSelectedTask"
            ><Icon icon="lucide:refresh-cw" />重新加载任务</el-button
          >
        </div>
        <template v-else-if="task">
          <section class="task-summary" aria-label="已选生产任务">
            <div class="item-heading">
              <h2>{{ task.taskNo }}</h2>
              <el-tag
                :type="
                  task.status === 'ACTIVE'
                    ? 'success'
                    : task.status === 'CANCELLED'
                      ? 'danger'
                      : 'info'
                "
                >{{ statusText(task.status) }}</el-tag
              >
            </div>
            <dl class="summary-grid">
              <div class="wide-field"
                ><dt>客户</dt><dd>{{ task.customerName }}</dd></div
              >
              <div
                ><dt>业务生产日期</dt><dd>{{ task.productionDate }}</dd></div
              >
              <div
                ><dt>班次</dt><dd>{{ task.shiftCode }}</dd></div
              >
              <div v-if="task.remark" class="wide-field"
                ><dt>任务备注</dt><dd>{{ task.remark }}</dd></div
              >
            </dl>
          </section>

          <el-radio-group
            v-if="canCreate && task.status === 'ACTIVE' && !pending"
            v-model="view"
            class="view-switch"
          >
            <el-radio-button label="录入产出" value="entry" />
            <el-radio-button label="生产记录" value="records" />
          </el-radio-group>

          <section
            v-if="view === 'entry' && task.status === 'ACTIVE' && canCreate && !pending"
            class="entry-section"
            aria-labelledby="production-entry-title"
          >
            <h2 id="production-entry-title">实际产出</h2>
            <el-alert
              v-if="submissionError"
              :title="submissionError"
              type="error"
              :closable="false"
              show-icon
            />
            <el-form
              ref="formRef"
              :model="form"
              :rules="rules"
              label-position="top"
              :disabled="submitting || !!cacheError"
              @submit.prevent="submitDraft"
            >
              <el-form-item label="型号" prop="productModel">
                <el-select
                  v-model="form.productModel"
                  filterable
                  remote
                  allow-create
                  default-first-option
                  :remote-method="loadModels"
                  :loading="modelLoading"
                  placeholder="选择或输入实际型号"
                  @visible-change="(open) => open && loadModels()"
                >
                  <el-option v-for="model in models" :key="model" :label="model" :value="model" />
                </el-select>
              </el-form-item>
              <div v-if="modelError" class="model-error" role="alert">
                <span>{{ modelError }}</span>
                <el-button link type="primary" @click="loadModels()">重试</el-button>
              </div>
              <FurnaceFields
                v-model:furnace-no="form.furnaceNo"
                v-model:furnace-layer="form.furnaceLayer"
                :customer-name="task.customerName"
                :product-model="form.productModel"
                :disabled="submitting || !!cacheError"
              />
              <el-form-item label="整包数" prop="outputQty">
                <el-input-number
                  v-model="form.outputQty"
                  :min="1"
                  :precision="0"
                  :step="1"
                  step-strictly
                  :controls="false"
                  aria-label="实际整包数"
                />
              </el-form-item>
              <el-form-item label="实称单包重量 kg" prop="packageWeight">
                <el-input-number
                  v-model="form.packageWeight"
                  :min="0.000001"
                  :precision="6"
                  :step="0.000001"
                  :controls="false"
                  aria-label="实称单包重量 kg"
                />
              </el-form-item>
              <el-form-item label="出炉日期（选填）" prop="furnaceOutDate">
                <el-date-picker
                  v-model="form.furnaceOutDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  clearable
                />
              </el-form-item>
              <el-form-item label="备注（选填）" prop="remark">
                <el-input
                  v-model="form.remark"
                  type="textarea"
                  :rows="2"
                  maxlength="1000"
                  show-word-limit
                />
              </el-form-item>
              <el-button
                type="primary"
                native-type="submit"
                class="save-draft"
                :loading="submitting"
                :disabled="!!cacheError"
              >
                <Icon icon="lucide:save" />保存草稿
              </el-button>
            </el-form>
          </section>

          <section v-else class="records-section" aria-labelledby="production-records-title">
            <div class="section-heading">
              <h2 id="production-records-title"
                >生产记录 <small>{{ outputTotal }} 条</small></h2
              >
              <el-tooltip content="刷新任务与生产记录">
                <el-button
                  circle
                  aria-label="刷新任务与生产记录"
                  :disabled="submitting || actingId !== undefined"
                  @click="loadSelectedTask"
                  ><Icon icon="lucide:refresh-cw"
                /></el-button>
              </el-tooltip>
            </div>
            <el-alert v-if="!canRead" title="无生产记录查询权限" type="info" :closable="false" />
            <template v-else>
              <el-alert
                v-if="outputActionError"
                :title="outputActionError"
                type="error"
                :closable="false"
                show-icon
              />
              <el-skeleton v-if="outputLoading" :rows="4" animated />
              <div v-else-if="outputError" class="error-state" role="alert"
                ><p>{{ outputError }}</p
                ><el-button @click="loadOutputs">重试</el-button></div
              >
              <el-empty
                v-else-if="!outputs.length"
                :image-size="64"
                description="该任务暂无生产记录"
              />
              <article
                v-for="row in outputLoading || outputError ? [] : outputs"
                :key="row.id"
                class="record-item"
              >
                <div class="item-heading"
                  ><h3>{{ row.productModel }}</h3
                  ><el-tag
                    :type="
                      row.status === 'DRAFT'
                        ? 'info'
                        : row.status === 'CANCELLED'
                          ? 'danger'
                          : 'success'
                    "
                    >{{ outputStatusText(row.status) }}</el-tag
                  ></div
                >
                <dl class="summary-grid">
                  <div
                    ><dt>炉号</dt><dd>{{ row.furnaceNo }}</dd></div
                  >
                  <div
                    ><dt>炉层</dt><dd>{{ row.furnaceLayer }}</dd></div
                  >
                  <div
                    ><dt>包数</dt><dd>{{ row.outputQty }}</dd></div
                  >
                  <div
                    ><dt>实称单包 kg</dt><dd>{{ row.packageWeight }}</dd></div
                  >
                  <div
                    ><dt>出炉日期</dt><dd>{{ row.furnaceOutDate || '-' }}</dd></div
                  >
                  <div
                    ><dt>录入人</dt><dd>{{ row.operatorName || '-' }}</dd></div
                  >
                  <div
                    ><dt>包码数</dt><dd>{{ packageCount(row.id) ?? '-' }}</dd></div
                  >
                  <div
                    ><dt>记录编号</dt><dd>{{ row.id }}</dd></div
                  >
                  <div v-if="row.remark" class="wide-field"
                    ><dt>备注</dt><dd>{{ row.remark }}</dd></div
                  >
                </dl>
                <div
                  v-if="task.status === 'ACTIVE' && (canConfirm || canGenerate)"
                  class="pda-actions"
                >
                  <el-button
                    v-if="canConfirm"
                    type="primary"
                    plain
                    :disabled="row.status !== 'DRAFT' || actingId !== undefined || submitting"
                    :loading="actingId === row.id"
                    @click="runAction(row, 'confirm')"
                    ><Icon icon="lucide:check" />确认产出</el-button
                  >
                  <el-button
                    v-if="canGenerate"
                    type="warning"
                    plain
                    :disabled="
                      row.status !== 'CONFIRMED' ||
                      actingId !== undefined ||
                      submitting ||
                      (packageCount(row.id) ?? 0) > 0
                    "
                    @click="runAction(row, 'generate')"
                    ><Icon icon="lucide:qr-code" />生成包码</el-button
                  >
                </div>
              </article>
              <el-pagination
                v-if="outputTotal > outputPageSize"
                v-model:current-page="outputPage"
                :page-size="outputPageSize"
                :total="outputTotal"
                :pager-count="5"
                layout="prev, pager, next"
                small
                @current-change="loadOutputs"
              />
            </template>
          </section>
          <section
            v-if="canReadLabels"
            class="labels-section"
            aria-labelledby="production-labels-title"
          >
            <h2 id="production-labels-title">任务标签</h2>
            <p v-if="packagesLoaded" class="label-count">已生成 {{ packages.length }} 个包码</p>
            <div v-if="packageError" class="error-state" role="alert"
              ><p>{{ packageError }}</p
              ><el-button :loading="packageLoading" @click="loadPackages">重试</el-button></div
            >
            <div class="pda-actions">
              <el-button @click="openLabels('history')"
                ><Icon icon="lucide:history" />标签历史</el-button
              >
              <el-button v-if="canPrint" type="primary" plain @click="openLabels('first')"
                ><Icon icon="lucide:printer" />首次标签</el-button
              >
              <el-button v-if="canReprint" @click="openLabels('reprint')"
                ><Icon icon="lucide:copy" />补打标签</el-button
              >
            </div>
          </section>
        </template>
      </template>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { useMessage } from '@/hooks/web/useMessage'
import { hasPermission } from '@/directives/permission/hasPermi'
import { generateUUID } from '@/utils'
import FurnaceFields from '@/views/rd/production/task/FurnaceFields.vue'
import { useUserStore } from '@/store/modules/user'
import { getTenantId } from '@/utils/auth'
import { RdCustomerProductApi } from '@/api/rd/masterdata/customer-product'
import {
  RdProductionTaskApi,
  type RdProductionTaskVO,
  type RdProductionTaskStatus
} from '@/api/rd/production/task'
import { RdProductionOutputApi, type RdProductionOutputVO } from '@/api/rd/production/output'
import { useTaskOutputs, outputStatusText } from '@/views/rd/production/task/useTaskOutputs'
import {
  bindProductionDraft,
  restoreProductionDraft,
  type BoundProductionDraft,
  type ProductionDraftFields
} from '@/views/rd/production/task/draftRequest'

defineOptions({ name: 'RdProductionEntry' })
const route = useRoute()
const message = useMessage()
const userStore = useUserStore()
const canQuery = computed(() => hasPermission(['rd:production-task:query']))
const canCreate = computed(() => hasPermission(['rd:production-output:create']))
const owner = { operatorId: userStore.user.id, tenantId: String(getTenantId() ?? '') }
const storageKey = `rd-production-draft:${owner.tenantId}:${owner.operatorId}`
const pending = shallowRef<Readonly<BoundProductionDraft>>()
const cacheError = ref('')
const submissionError = ref('')
const submitting = ref(false)
const taskStatus = ref<RdProductionTaskStatus>('ACTIVE')
const taskKeyword = ref('')
const taskPage = ref(1)
const tasks = ref<RdProductionTaskVO[]>([])
const taskTotal = ref(0)
const taskLoading = ref(false)
const taskError = ref('')
const selectedId = ref<number>()
const task = ref<RdProductionTaskVO>()
const detailLoading = ref(false)
const detailError = ref('')
const view = ref<'entry' | 'records'>('entry')
const formRef = ref<FormInstance>()
const emptyForm = (): ProductionDraftFields => ({
  productModel: '',
  furnaceNo: '',
  furnaceLayer: '',
  outputQty: undefined,
  packageWeight: undefined,
  furnaceOutDate: '',
  remark: ''
})
const form = reactive(emptyForm())
const models = ref<string[]>([])
const modelLoading = ref(false)
const modelError = ref('')
let taskListRequest = 0
let detailRequest = 0
let modelRequest = 0

const rules: FormRules = {
  productModel: [{ required: true, message: '请选择型号', trigger: 'change' }],
  furnaceNo: [{ required: true, whitespace: true, message: '请填写炉号', trigger: 'blur' }],
  furnaceLayer: [{ required: true, whitespace: true, message: '请填写炉层', trigger: 'blur' }],
  outputQty: [
    {
      validator: (_rule, value, done) =>
        Number.isSafeInteger(value) && value > 0 ? done() : done(new Error('包数须为正整数')),
      trigger: 'blur'
    }
  ],
  packageWeight: [
    {
      validator: (_rule, value, done) =>
        Number.isFinite(value) && value >= 0.000001
          ? done()
          : done(new Error('请填写实称单包重量')),
      trigger: 'blur'
    }
  ]
}
const statusText = (status: string) =>
  ({ ACTIVE: '登记中', CLOSED: '已结束登记', CANCELLED: '已作废' })[status] || status
const {
  outputs,
  total: outputTotal,
  page: outputPage,
  pageSize: outputPageSize,
  loading: outputLoading,
  error: outputError,
  actionError: outputActionError,
  actingId,
  packages,
  packagesLoaded,
  packageLoading,
  packageError,
  packageCount,
  canRead,
  canConfirm,
  canGenerate,
  canReadLabels,
  canPrint,
  canReprint,
  loadOutputs,
  loadPackages,
  refresh: refreshOutputs,
  runAction,
  openLabels
} = useTaskOutputs(task)

const loadTasks = async () => {
  if (!canQuery.value) return
  const request = ++taskListRequest
  taskLoading.value = true
  taskError.value = ''
  try {
    const data = await RdProductionTaskApi.getPage({
      pageNo: taskPage.value,
      pageSize: 10,
      status: taskStatus.value,
      taskNo: taskKeyword.value.trim() || undefined
    })
    if (request !== taskListRequest) return
    tasks.value = data.list
    taskTotal.value = data.total
  } catch {
    if (request === taskListRequest) {
      tasks.value = []
      taskTotal.value = 0
      taskError.value = '任务列表加载失败。'
    }
  } finally {
    if (request === taskListRequest) taskLoading.value = false
  }
}
const searchTasks = () => {
  taskPage.value = 1
  void loadTasks()
}
const loadModels = async (keyword = '') => {
  const customerName = task.value?.customerName
  const id = task.value?.id
  const request = ++modelRequest
  modelError.value = ''
  if (!customerName || pending.value) return
  modelLoading.value = true
  try {
    const data = await RdCustomerProductApi.getModelOptions({ customerName, keyword, limit: 100 })
    if (request === modelRequest && task.value?.id === id) models.value = data
  } catch {
    if (request === modelRequest) modelError.value = '客户型号加载失败。'
  } finally {
    if (request === modelRequest) modelLoading.value = false
  }
}
const applyTask = (data: RdProductionTaskVO) => {
  if (task.value && task.value.customerName !== data.customerName && !pending.value) {
    form.productModel = ''
    models.value = []
  }
  task.value = data
}
const loadSelectedTask = async () => {
  const id = selectedId.value
  if (!id || !canQuery.value) return
  const request = ++detailRequest
  detailLoading.value = true
  detailError.value = ''
  try {
    const data = await RdProductionTaskApi.get(id)
    if (request !== detailRequest || selectedId.value !== id) return
    const sameTask = task.value?.id === id
    applyTask(data)
    if (data.status !== 'ACTIVE' || !canCreate.value || pending.value) view.value = 'records'
    if (sameTask) await refreshOutputs()
    if (canCreate.value && data.status === 'ACTIVE' && !pending.value) void loadModels()
  } catch {
    if (request === detailRequest) {
      task.value = undefined
      detailError.value = '任务详情加载失败。'
    }
  } finally {
    if (request === detailRequest) detailLoading.value = false
  }
}
const openTask = (id: number) => {
  if (submitting.value || actingId.value !== undefined) return
  if (pending.value && pending.value.payload.productionTaskId !== id) {
    message.warning('请先核定待重试记录，不能换任务重试。')
    return
  }
  if (selectedId.value !== id) {
    task.value = undefined
    modelRequest++
    models.value = []
    modelError.value = ''
    modelLoading.value = false
    if (!pending.value) {
      Object.assign(form, emptyForm())
      submissionError.value = ''
    }
  }
  selectedId.value = id
  view.value = canCreate.value && !pending.value ? 'entry' : 'records'
  void loadSelectedTask()
}
const backToTasks = () => {
  if (submitting.value || pending.value || actingId.value !== undefined) return
  detailRequest++
  selectedId.value = undefined
  task.value = undefined
  void loadTasks()
}

const assertOwner = () => {
  if (userStore.user.id !== owner.operatorId || String(getTenantId() ?? '') !== owner.tenantId) {
    throw new Error('登录身份已变更，请使用原录入身份核定这笔记录。')
  }
}
const submitDraft = async () => {
  if (submitting.value || !canCreate.value || cacheError.value) return
  submitting.value = true
  submissionError.value = ''
  try {
    assertOwner()
    let draft = pending.value
    if (!draft) {
      if (
        !task.value ||
        task.value.status !== 'ACTIVE' ||
        !(await formRef.value?.validate().catch(() => false))
      )
        return
      const displayed = task.value
      const fresh = await RdProductionTaskApi.get(displayed.id)
      applyTask(fresh)
      if (fresh.status !== 'ACTIVE' || fresh.version !== displayed.version) {
        submissionError.value = '任务信息已变更，请核对当前客户、生产日期和班次后重新提交。'
        if (fresh.status !== 'ACTIVE') view.value = 'records'
        else void loadModels()
        return
      }
      draft = bindProductionDraft(fresh, { ...form }, owner, generateUUID())
      pending.value = draft
    }
    // Persist before sending; a lost response must never create a fresh request identity.
    sessionStorage.setItem(storageKey, JSON.stringify(draft))
    const id = await RdProductionOutputApi.create(draft.payload)
    if (!Number.isSafeInteger(id) || id <= 0)
      throw new Error('服务器未返回有效记录编号，请重试原请求。')
    const saved: RdProductionOutputVO = await RdProductionOutputApi.get(id)
    if (
      saved.id !== id ||
      saved.productionTaskId !== draft.payload.productionTaskId ||
      (saved.clientRequestId !== undefined &&
        saved.clientRequestId !== draft.payload.clientRequestId) ||
      (saved.operatorId !== undefined && saved.operatorId !== owner.operatorId)
    )
      throw new Error('返回记录与绑定任务不符，请保留原请求核对。')
    assertOwner()
    sessionStorage.removeItem(storageKey)
    pending.value = undefined
    Object.assign(form, emptyForm())
    view.value = 'records'
    outputPage.value = 1
    message.success(
      saved.status === 'DRAFT'
        ? `草稿已保存，记录编号 ${saved.id}`
        : `原请求已核定：记录 ${saved.id}（${outputStatusText(saved.status)}）`
    )
    await refreshOutputs()
  } catch (cause) {
    submissionError.value =
      cause instanceof Error ? cause.message : '提交结果尚未核定，请重试原请求或先核对生产记录。'
    if (!pending.value) message.error(submissionError.value)
  } finally {
    submitting.value = false
  }
}
const releasePending = async () => {
  if (submitting.value) return
  try {
    assertOwner()
    await message.confirm(
      '已核对服务器生产记录，确定解除本机待重试绑定？这不会删除服务器记录；下一次保存会成为新的提交，可能造成重复产出。'
    )
    assertOwner()
    sessionStorage.removeItem(storageKey)
    pending.value = undefined
    cacheError.value = ''
    submissionError.value = ''
    Object.assign(form, emptyForm())
    view.value = 'records'
  } catch (cause) {
    if (cause !== 'cancel' && cause !== 'close') message.error('本机记录未清理，请保留原请求核对。')
  }
}
const restoreSession = () => {
  try {
    const serialized = sessionStorage.getItem(storageKey)
    if (!serialized) return
    pending.value = restoreProductionDraft(serialized, owner)
    openTask(pending.value.payload.productionTaskId)
  } catch {
    cacheError.value = '本机待重试记录无法读取。请先核对服务器记录，不能直接创建新提交。'
  }
}
const warnBeforeUnload = (event: BeforeUnloadEvent) => {
  if (!pending.value && !submitting.value) return
  event.preventDefault()
  event.returnValue = ''
}
onBeforeRouteLeave(() => {
  if (submitting.value || actingId.value !== undefined) return false
  return true
})
watch(
  () => route.query.productionTaskId,
  (value) => {
    const id = Number(value)
    if (value && Number.isSafeInteger(id) && id > 0 && !pending.value) openTask(id)
  }
)
onMounted(() => {
  restoreSession()
  if (!pending.value) {
    const id = Number(route.query.productionTaskId)
    if (Number.isSafeInteger(id) && id > 0) openTask(id)
    else void loadTasks()
  }
  window.addEventListener('beforeunload', warnBeforeUnload)
})
onUnmounted(() => window.removeEventListener('beforeunload', warnBeforeUnload))
</script>

<style scoped>
.production-pda {
  width: 100%;
  max-width: 560px;
  min-width: 0;
  padding: 16px;
  margin: 0 auto;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
  box-sizing: border-box;
}

.pda-header,
.item-heading,
.section-heading {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pda-header {
  margin-bottom: 16px;
}

.pda-header h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.production-pda h2,
.production-pda h3 {
  min-width: 0;
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.task-selection,
.task-list {
  display: grid;
  gap: 12px;
}

.status-switch,
.view-switch {
  display: flex;
  width: 100%;
}

.status-switch :deep(.el-radio-button),
.view-switch :deep(.el-radio-button) {
  min-width: 0;
  flex: 1;
}

.status-switch :deep(.el-radio-button__inner),
.view-switch :deep(.el-radio-button__inner) {
  width: 100%;
  min-height: 44px;
  padding: 12px 8px;
  box-sizing: border-box;
}

.task-pick {
  display: grid;
  width: 100%;
  min-width: 0;
  padding: 16px 0;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--el-border-color-light);
  gap: 8px;
}

.task-pick:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.task-pick:hover {
  background: var(--el-fill-color-light);
}

.task-pick strong,
.task-customer,
.task-secondary span,
.task-manager {
  min-width: 0;
  overflow-wrap: anywhere;
}

.task-secondary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.task-secondary,
.task-manager,
.label-count,
.production-pda small {
  font-size: 13px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.task-summary,
.pending-band,
.entry-section,
.records-section,
.labels-section {
  padding: 16px 0;
  border-bottom: 1px solid var(--el-border-color-light);
}

.pending-band {
  padding-top: 0;
  margin-bottom: 16px;
}

.summary-grid {
  display: grid;
  margin: 12px 0;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.summary-grid > div {
  min-width: 0;
}

.summary-grid dt {
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.summary-grid dd {
  margin: 0;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.wide-field {
  grid-column: 1 / -1;
}

.request-id {
  font-family: monospace;
  font-size: 12px;
}

.entry-section h2,
.labels-section h2,
.section-heading {
  margin-bottom: 16px;
}

.form-pair {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.entry-section :deep(.el-select),
.entry-section :deep(.el-input-number),
.entry-section :deep(.el-date-editor),
.save-draft {
  width: 100%;
  min-width: 0;
}

.production-pda :deep(.el-input__wrapper),
.production-pda :deep(.el-select__wrapper) {
  min-height: 44px;
  box-sizing: border-box;
}

.production-pda :deep(.el-form-item__label) {
  height: auto;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.production-pda :deep(.el-button) {
  min-height: 44px;
  flex-shrink: 0;
  gap: 6px;
}

.production-pda :deep(.el-button.is-circle) {
  width: 44px;
}

.pda-actions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 8px;
}

.pda-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.record-item {
  padding: 16px 0;
  border-bottom: 1px solid var(--el-border-color-light);
}

.error-state,
.model-error {
  padding: 12px 0;
  font-size: 13px;
  color: var(--el-color-danger);
  overflow-wrap: anywhere;
}

.error-state p {
  margin: 0 0 12px;
}

.cache-action {
  margin: 12px 0;
}

.production-pda :deep(.el-pagination) {
  margin-top: 16px;
  justify-content: center;
}

@media (width <= 390px) {
  .production-pda {
    padding: 12px;
  }
}
</style>
