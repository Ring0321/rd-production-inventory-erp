<template>
  <div class="package-trace-page">
    <header class="trace-header">
      <div class="trace-header__inner">
        <div class="brand" aria-label="宁夏天净隆鼎碳化硅有限公司">
          <img :src="logoUrl" alt="天净隆鼎" class="brand__logo" />
          <div>
            <strong>宁夏天净隆鼎碳化硅有限公司</strong>
            <span>货物数字身份与追溯</span>
          </div>
        </div>
        <div class="header-actions">
          <el-tooltip v-if="hasAccessToken" content="PDA 工作台" placement="bottom">
            <el-button
              circle
              aria-label="PDA 工作台"
              @click="router.push({ name: 'PdaWorkspace' })"
            >
              <Icon icon="lucide:smartphone" />
            </el-button>
          </el-tooltip>
          <el-tooltip content="刷新货物信息" placement="bottom">
            <el-button circle :loading="loading" aria-label="刷新货物信息" @click="loadTrace">
              <Icon icon="lucide:refresh-cw" />
            </el-button>
          </el-tooltip>
          <el-button v-if="!hasAccessToken" type="primary" @click="goLogin">
            <Icon icon="lucide:log-in" class="mr-6px" />
            内部登录
          </el-button>
        </div>
      </div>
    </header>

    <main class="trace-main">
      <section v-if="loading" class="trace-surface" aria-live="polite">
        <el-skeleton :rows="8" animated />
      </section>

      <section v-else-if="!trace" class="trace-surface empty-state">
        <el-empty description="未查询到该包货物信息">
          <el-button type="primary" @click="loadTrace">重新查询</el-button>
        </el-empty>
      </section>

      <article v-else class="trace-surface">
        <section class="identity-band" aria-labelledby="package-heading">
          <div class="identity-band__main">
            <span class="identity-eyebrow">一包一码</span>
            <h1 id="package-heading">{{ trace.packageCode }}</h1>
            <p>{{ trace.productName || trace.productModel || '碳化硅产品' }}</p>
          </div>
          <div class="identity-band__status">
            <el-tag :type="statusTagType" effect="dark" size="large">
              {{ trace.statusName || trace.status }}
            </el-tag>
            <span>{{ trace.internalView ? 'ERP 内部视图' : '公开追溯视图' }}</span>
          </div>
        </section>

        <section class="feature-strip" aria-label="扫码吨袋作业概览">
          <dl v-for="item in featureCards" :key="item.label" class="trace-feature-card">
            <dt>{{ item.label }}</dt>
            <dd>{{ item.value }}</dd>
            <p>{{ item.hint }}</p>
          </dl>
        </section>

        <section class="detail-section action-section" aria-labelledby="action-heading">
          <div class="section-heading">
            <Icon icon="lucide:scan-line" />
            <div>
              <h2 id="action-heading">现场办理</h2>
            </div>
          </div>
          <div class="next-action-card">
            <div class="next-action-card__icon">
              <Icon :icon="fieldAction.icon" />
            </div>
            <div class="next-action-card__body">
              <div class="next-action-title">
                <strong>{{ fieldAction.title }}</strong>
                <span>{{ fieldAction.role }}</span>
              </div>
              <p>{{ fieldAction.detail }}</p>
              <el-alert
                v-if="workContextUnavailable"
                title="关联任务读取失败，请刷新后办理"
                type="warning"
                :closable="false"
              />
              <div v-if="traceActions.length" class="action-entry-list">
                <el-tooltip
                  v-for="action in traceActions"
                  :key="action.key"
                  :content="action.description"
                  placement="top"
                >
                  <el-button
                    :type="action.type"
                    :plain="!action.primary"
                    @click="runTraceAction(action)"
                  >
                    <Icon :icon="action.icon" class="mr-6px" />
                    {{ action.label }}
                  </el-button>
                </el-tooltip>
              </div>
              <p v-else class="action-entry-empty">
                {{ actionEntryEmptyText }}
              </p>
            </div>
          </div>
        </section>

        <section class="detail-section" aria-labelledby="base-info-heading">
          <div class="section-heading">
            <Icon icon="lucide:package-check" />
            <div>
              <h2 id="base-info-heading">货物信息</h2>
              <p>生产与包装环节登记的信息</p>
            </div>
          </div>
          <dl class="detail-grid">
            <div>
              <dt>型号 / 规格</dt>
              <dd>{{ trace.productModel || '-' }}</dd>
            </div>
            <div>
              <dt>炉号</dt>
              <dd>{{ trace.furnaceNo || '-' }}</dd>
            </div>
            <div>
              <dt>炉层</dt>
              <dd>{{ trace.furnaceLayer || '-' }}</dd>
            </div>
            <div>
              <dt>单包重量</dt>
              <dd>{{ weightText }}</dd>
            </div>
            <div>
              <dt>生产日期</dt>
              <dd>{{ trace.productionDate || '-' }}</dd>
            </div>
            <div>
              <dt>出炉日期</dt>
              <dd>{{ trace.furnaceOutDate || '-' }}</dd>
            </div>
            <div>
              <dt>生产班次</dt>
              <dd>{{ trace.shiftCode || '-' }}</dd>
            </div>
            <div>
              <dt>质检结论</dt>
              <dd>
                <span :class="['quality-result', `quality-result--${qualityTone}`]">
                  <Icon :icon="qualityIcon" />
                  <span class="quality-result__text">{{ qualityText }}</span>
                </span>
              </dd>
            </div>
            <template v-if="trace.internalView && trace.qualityCoverageCount != null">
              <div
                ><dt>检验范围 / 抽样</dt
                ><dd
                  >{{ trace.qualityCoverageCount }} /
                  {{ trace.qualitySampleCount ?? '未记录' }} 包</dd
                ></div
              >
              <div v-if="trace.qualitySampled != null"
                ><dt>本包检验角色</dt
                ><dd>{{ trace.qualitySampled ? '实际抽样包' : '检验覆盖包' }}</dd></div
              >
            </template>
          </dl>
        </section>

        <section
          v-if="trace.internalView"
          class="detail-section internal-section"
          aria-labelledby="internal-heading"
        >
          <div class="section-heading">
            <Icon icon="lucide:shield-check" />
            <div>
              <h2 id="internal-heading">内部作业信息</h2>
              <p>仅向有权限的 ERP 用户显示</p>
            </div>
          </div>
          <dl class="detail-grid detail-grid--internal">
            <div>
              <dt>所属客户</dt>
              <dd>{{ trace.customerName || '-' }}</dd>
            </div>
            <div>
              <dt>{{ trace.status === 'OUTBOUNDED' ? '出库前库位' : '当前库位' }}</dt>
              <dd>{{ trace.locationCode || '暂未上架' }}</dd>
            </div>
            <div>
              <dt>生产记录号</dt>
              <dd>{{ trace.productionOutputId || '-' }}</dd>
            </div>
            <div v-if="trace.productionTaskNo">
              <dt>生产任务</dt>
              <dd>{{ trace.productionTaskNo }}</dd>
            </div>
          </dl>
        </section>

        <section
          v-if="trace.internalView"
          v-loading="batchLoading"
          class="detail-section batch-section"
          aria-labelledby="batch-heading"
        >
          <div class="section-heading">
            <Icon icon="lucide:boxes" />
            <div>
              <h2 id="batch-heading">同批次吨袋概览</h2>
              <p>扫同一生产记录中的任意一包，即可定位该批吨袋清单</p>
            </div>
          </div>

          <el-empty
            v-if="!trace.productionOutputId"
            description="当前包码未关联生产记录，暂不能展开同批次信息"
            :image-size="72"
          />
          <el-alert
            v-else-if="batchUnavailable"
            title="同批次信息读取失败，请刷新重试"
            type="warning"
            :closable="false"
          />
          <template v-else>
            <div class="batch-summary-grid">
              <div class="batch-summary-card">
                <span>同批包数</span>
                <strong>{{ batchStats.totalPackages }} 包</strong>
              </div>
              <div class="batch-summary-card">
                <span>合计重量</span>
                <strong>{{ batchStats.totalWeightText }}</strong>
              </div>
              <div class="batch-summary-card">
                <span>涉及库位（含历史）</span>
                <strong>{{ batchStats.locationCount }} 个</strong>
              </div>
              <div class="batch-summary-card">
                <span>状态分布</span>
                <strong>{{ batchStats.statusText }}</strong>
              </div>
            </div>

            <div v-if="batchPreview.length" class="batch-package-list" role="list">
              <div
                v-for="pkg in batchPreview"
                :key="pkg.packageCode"
                :class="[
                  'batch-package-row',
                  { 'batch-package-row--current': pkg.packageCode === trace.packageCode }
                ]"
                role="listitem"
              >
                <div class="batch-package-main">
                  <el-link
                    :href="`/pkg/${encodeURIComponent(pkg.packageCode)}`"
                    @click.prevent="
                      router.push({ path: `/pkg/${encodeURIComponent(pkg.packageCode)}` })
                    "
                  >
                    {{ pkg.packageCode }}
                  </el-link>
                  <span>{{ pkg.productModel || trace.productModel || '-' }}</span>
                </div>
                <div class="batch-package-meta">
                  <el-tag :type="packageStatusTagType(pkg.status)" size="small">
                    {{ packageStatusName(pkg.status) }}
                  </el-tag>
                  <span>{{ pkg.locationCode || '暂未上架' }}</span>
                  <span>{{ formatPackageWeight(pkg.packageWeight) }}</span>
                </div>
              </div>
            </div>
            <p v-if="batchPackages.length > batchPreview.length" class="batch-more">
              已展示当前包及前 {{ batchPreview.length - 1 }} 包，完整清单可在包码台账中查看。
            </p>
          </template>
        </section>

        <section class="detail-section timeline-section" aria-labelledby="timeline-heading">
          <div class="section-heading">
            <Icon icon="lucide:history" />
            <div>
              <h2 id="timeline-heading">流转记录</h2>
            </div>
          </div>
          <div class="timeline-filter">
            <label for="timeline-stage">流转阶段</label>
            <el-select
              id="timeline-stage"
              v-model="timelineStage"
              class="timeline-filter__select"
              size="small"
              aria-label="流转阶段"
            >
              <el-option label="全部阶段" value="ALL" />
              <el-option
                v-for="stage in timelineStages"
                :key="stage.value"
                :label="stage.label"
                :value="stage.value"
              />
            </el-select>
          </div>
          <el-timeline v-if="filteredEvents.length" class="trace-timeline">
            <el-timeline-item
              v-for="(event, index) in filteredEvents"
              :key="`${event.stage}-${event.eventTime}-${event.title}-${index}`"
              :timestamp="dateTimeText(event.eventTime)"
              :type="eventType(event.stage)"
              placement="top"
            >
              <div class="timeline-milestone">
                <el-tag :type="eventType(event.stage)" effect="plain" size="small">
                  {{ eventStageLabel(event.stage) }}
                </el-tag>
                <strong>{{ event.title }}</strong>
              </div>
              <p v-if="event.detail">{{ event.detail }}</p>
            </el-timeline-item>
          </el-timeline>
          <el-empty
            v-else
            :description="timelineStage === 'ALL' ? '暂无流转记录' : '该阶段暂无流转记录'"
            :image-size="72"
          />
        </section>

        <footer class="trace-footer">
          <span>数据来源：天净隆鼎 ERP</span>
          <span>查询时间：{{ queryTime }}</span>
        </footer>
      </article>
    </main>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import logoUrl from '@/assets/imgs/logo.png'
import {
  RdPackageUnitApi,
  type RdPackageStatus,
  type RdPackageTraceVO,
  type RdPackageWorkContextVO,
  type RdPackageUnitVO
} from '@/api/rd/stock/package'
import { getAccessToken } from '@/utils/auth'
import { formatDate } from '@/utils/formatTime'
import { hasPermission } from '@/directives/permission/hasPermi'

defineOptions({ name: 'RdPackageTrace' })

type TagType = 'success' | 'warning' | 'info' | 'danger'
type ButtonType = '' | 'primary' | 'success' | 'warning' | 'danger' | 'info'
type RouteQueryValue = string | number
type TraceAction = {
  key: string
  label: string
  description: string
  icon: string
  type?: ButtonType
  primary?: boolean
  routeName?: string
  query?: Record<string, RouteQueryValue>
  handler?: 'login' | 'refresh'
}

const route = useRoute()
const router = useRouter()
const pdaMode = computed(() => route.query.workMode === 'pda')
const loading = ref(false)
const batchLoading = ref(false)
const trace = ref<RdPackageTraceVO | null>(null)
const batchPackages = ref<RdPackageUnitVO[]>([])
const batchUnavailable = ref(false)
const workContext = ref<RdPackageWorkContextVO | null>(null)
const workContextUnavailable = ref(false)
const queryTime = ref('-')
const timelineStage = ref('ALL')
const timelineStages: { value: string; label: string; type: TagType }[] = [
  { value: 'PRODUCTION', label: '生产', type: 'info' },
  { value: 'LABEL', label: '贴码', type: 'info' },
  { value: 'QUALITY', label: '质检', type: 'warning' },
  { value: 'PUTAWAY', label: '上架', type: 'success' },
  { value: 'LOADING', label: '装车', type: 'warning' },
  { value: 'EXCEPTION', label: '异常', type: 'danger' },
  { value: 'STOCK', label: '库存', type: 'success' }
]
const filteredEvents = computed(() => {
  const events = trace.value?.events || []
  return timelineStage.value === 'ALL'
    ? events
    : events.filter((event) => event.stage === timelineStage.value)
})
let traceRequestSequence = 0

const packageCode = computed(() => String(route.params.packageCode || '').trim())
const hasAccessToken = computed(() => Boolean(getAccessToken()))
const canViewInternal = computed(
  () => hasAccessToken.value && hasPermission(['rd:package-unit:query'])
)
const statusTagType = computed<TagType>(() => {
  if (!trace.value) return 'info'
  return packageStatusTagType(trace.value.status)
})
const weightText = computed(() => {
  if (trace.value?.packageWeight === undefined || trace.value?.packageWeight === null) return '-'
  return `${trace.value.packageWeight} kg`
})
const qualityDecision = computed(() => {
  const status = String(trace.value?.qualityStatus || '')
    .trim()
    .toUpperCase()
  if (['DRAFT', 'SUBMITTED', 'CORRECTED', 'RESUBMITTED'].includes(status)) return status
  return String(trace.value?.qualityConclusion || status)
    .trim()
    .toUpperCase()
})
const qualityTone = computed(() => {
  if (['PASS', 'PASSED'].includes(qualityDecision.value)) return 'success'
  if (['REJECT', 'REJECTED'].includes(qualityDecision.value)) return 'danger'
  return 'pending'
})
const qualityText = computed(() => {
  if (qualityTone.value === 'success') return '合格'
  if (qualityTone.value === 'danger') return '不合格'
  const statusText: Record<string, string> = {
    DRAFT: '草稿（未提交）',
    SUBMITTED: '已提交，待审核',
    CORRECTED: '已更正（未提交）',
    RESUBMITTED: '已重提，待审核',
    REVIEWED: '已审核'
  }
  return statusText[qualityDecision.value] || qualityDecision.value || '待质检'
})
const qualityIcon = computed(() => {
  if (qualityTone.value === 'success') return 'lucide:circle-check'
  if (qualityTone.value === 'danger') return 'lucide:circle-x'
  return 'lucide:clock-3'
})
const fieldAction = computed(() => {
  const task = workContext.value?.tasks[0]
  if (task) {
    return {
      title: task.type === 'LOADING' ? '已关联装车任务' : '已关联上架任务',
      role: task.myTask ? '叉车司机' : '仓管员',
      detail: `${task.taskNo} · 按任务处理；扫码确认和库位登记由仓管员负责。`,
      icon: task.type === 'LOADING' ? 'lucide:truck' : 'lucide:warehouse'
    }
  }
  if (workContext.value?.pickupSlipNo) {
    return {
      title: '已关联提货单',
      role: '商务',
      detail: workContext.value.pickupSlipNo,
      icon: 'lucide:file-check-2'
    }
  }
  return getFieldAction(trace.value?.status)
})
const traceActions = computed<TraceAction[]>(() => buildTraceActions())
const actionEntryEmptyText = computed(() => {
  if (!hasAccessToken.value)
    return '内部登录后，系统会按岗位权限显示生产、质检、入库、出库等办理入口。'
  return '当前状态或当前账号权限下暂无可直接办理的动作，本页仅保留查询和追溯。'
})
const batchStats = computed(() => {
  const packages = batchPackages.value
  const totalWeight = packages.reduce((sum, pkg) => sum + Number(pkg.packageWeight || 0), 0)
  const locations = new Set(packages.map((pkg) => pkg.locationCode).filter(Boolean))
  const statusMap = packages.reduce<Record<string, number>>((result, pkg) => {
    const name = packageStatusName(pkg.status)
    result[name] = (result[name] || 0) + 1
    return result
  }, {})
  const statusText = Object.entries(statusMap)
    .slice(0, 3)
    .map(([name, count]) => `${name}${count}`)
    .join(' / ')

  return {
    totalPackages: packages.length,
    totalWeightText: packages.length ? `${totalWeight} kg` : '-',
    locationCount: locations.size,
    statusText: statusText || '-'
  }
})
const batchPreview = computed(() => {
  if (!trace.value) return []
  const current = batchPackages.value.find((pkg) => pkg.packageCode === trace.value?.packageCode)
  const others = batchPackages.value.filter((pkg) => pkg.packageCode !== trace.value?.packageCode)
  return [current, ...others].filter(Boolean).slice(0, 9) as RdPackageUnitVO[]
})
const featureCards = computed(() => {
  if (!trace.value) return []
  return [
    {
      label: '当前吨袋',
      value: trace.value.statusName || packageStatusName(trace.value.status),
      hint: '一包一码独立追溯'
    },
    {
      label: '同批次识别',
      value: trace.value.internalView
        ? batchLoading.value
          ? '读取中'
          : batchUnavailable.value
            ? '读取失败'
            : `${batchStats.value.totalPackages} 包 / ${batchStats.value.totalWeightText}`
        : '内部登录后查看',
      hint: '扫一包带出同生产记录'
    },
    {
      label: trace.value.status === 'OUTBOUNDED' ? '出库前库位' : '当前库位',
      value: trace.value.internalView ? trace.value.locationCode || '暂未上架' : '内部可见',
      hint: trace.value.status === 'OUTBOUNDED' ? '仅供历史追溯，当前已离库' : '库管确认后同步'
    },
    {
      label: '下一步',
      value: fieldAction.value.title,
      hint: `建议岗位：${fieldAction.value.role}`
    }
  ]
})

const packageStatusName = (status?: RdPackageStatus | string) => {
  const statusMap: Record<string, string> = {
    CREATED: '已建包',
    LABELED: '已贴码',
    QC_PENDING: '待质检',
    QC_PASSED: '质检合格',
    QC_REJECTED: '质检不合格',
    INBOUND_PENDING: '待入库',
    IN_STOCK: '在库',
    OUTBOUND_PENDING: '待出库',
    OUTBOUNDED: '已出库',
    VOIDED: '已作废',
    STOCKTAKE_MISSING: '盘点缺失'
  }
  return status ? statusMap[status] || status : '-'
}

const packageStatusTagType = (status?: RdPackageStatus | string): TagType => {
  if (['QC_REJECTED', 'STOCKTAKE_MISSING', 'VOIDED'].includes(String(status))) return 'danger'
  if (['QC_PENDING', 'INBOUND_PENDING', 'OUTBOUND_PENDING'].includes(String(status)))
    return 'warning'
  if (['QC_PASSED', 'IN_STOCK', 'OUTBOUNDED'].includes(String(status))) return 'success'
  return 'info'
}

const formatPackageWeight = (weight?: number) =>
  weight === undefined || weight === null ? '-' : `${weight} kg`

const cleanQuery = (query: Record<string, unknown>) => {
  return Object.entries(query).reduce<Record<string, RouteQueryValue>>((result, [key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      result[key] = value as RouteQueryValue
    }
    return result
  }, {})
}

const baseActionQuery = () =>
  cleanQuery({
    packageCode: trace.value?.packageCode,
    productionOutputId: trace.value?.productionOutputId,
    locationCode: trace.value?.locationCode,
    status: trace.value?.status
  })

const canDo = (permissions: string[]) => hasAccessToken.value && hasPermission(permissions)

const buildTraceActions = () => {
  if (!trace.value) return []
  if (!hasAccessToken.value) {
    return [
      {
        key: 'login',
        label: '内部登录办理',
        description: '登录 ERP 后按岗位权限显示生产、质检、入库、出库等入口。',
        icon: 'lucide:log-in',
        type: 'primary',
        primary: true,
        handler: 'login'
      }
    ] as TraceAction[]
  }

  const status = trace.value.status
  const query = baseActionQuery()
  const actions: TraceAction[] = []
  const addAction = (permissions: string[], action: TraceAction) => {
    if (
      canDo(permissions) &&
      (!action.routeName || router.hasRoute(action.routeName)) &&
      !actions.some((item) => item.key === action.key)
    )
      actions.push(action)
  }

  if (trace.value.productionTaskId) {
    addAction(['rd:production-task:query'], {
      key: 'production-task',
      label: '生产任务',
      description: '查看原生产任务及其产出记录',
      icon: 'lucide:clipboard-list',
      type: '',
      routeName:
        pdaMode.value && canDo(['rd:production-output:create'])
          ? 'PdaProduction'
          : 'RdProductionTask',
      query: { productionTaskId: trace.value.productionTaskId }
    })
    addAction(['rd:label-print:print'], {
      key: 'task-labels',
      label: '任务标签',
      description: '按原生产任务查询和打印标签',
      icon: 'lucide:printer',
      type: '',
      routeName: 'RdPackageUnit',
      query: { productionTaskId: trace.value.productionTaskId }
    })
  }

  for (const task of workContext.value?.tasks || []) {
    const taskQuery = { ...query, taskId: task.taskId }
    if (task.type === 'LOADING') {
      addAction(['rd:loading-task:dispatch', 'rd:loading-task:query'], {
        key: 'loading-review',
        label: canDo(['rd:loading-task:dispatch']) ? '出库扫码复核' : '查看装车任务',
        description: '打开该包关联的装车任务；由仓管核对实际装车包件。',
        icon: 'lucide:clipboard-list',
        routeName: 'RdLoadingDispatch',
        query: { ...taskQuery, mode: canDo(['rd:loading-task:dispatch']) ? 'scan' : 'view' },
        type: 'primary',
        primary: true
      })
      if (task.myTask)
        addAction(['rd:loading-task:execute'], {
          key: 'driver-loading',
          label: '查看本人装车任务',
          description: '查看本次派单的货位和货物资料。',
          icon: 'lucide:truck',
          routeName: pdaMode.value ? 'PdaDriverLoading' : 'RdDriverLoading',
          query: taskQuery,
          type: 'primary',
          primary: true
        })
    } else {
      addAction(['rd:putaway-task:query'], {
        key: 'putaway-workbench',
        label: canDo(['rd:putaway-task:confirm']) ? '入库扫码核验' : '查看上架任务',
        description: '打开该包关联的上架任务。',
        icon: 'lucide:warehouse',
        routeName: 'RdPutawayWorkbench',
        query: { ...taskQuery, mode: canDo(['rd:putaway-task:confirm']) ? 'scan' : 'view' },
        type: 'success',
        primary: true
      })
      if (task.myTask)
        addAction(['rd:putaway-task:execute'], {
          key: 'driver-putaway',
          label: '查看本人上架任务',
          description: '查看本次派单的目标库位和货物资料。',
          icon: 'lucide:forklift',
          routeName: pdaMode.value ? 'PdaDriverPutaway' : 'RdDriverPutaway',
          query: taskQuery
        })
    }
  }
  if (workContext.value?.pickupSlipId)
    addAction(['rd:pickup-slip:query'], {
      key: 'pickup-slip',
      label: '关联提货单',
      description: '查看该包对应的提货单。',
      icon: 'lucide:file-check-2',
      routeName: 'RdPickupSlip',
      query: { pickupSlipId: workContext.value.pickupSlipId },
      type: 'info'
    })

  addAction(['rd:package-unit:query'], {
    key: 'package-ledger',
    label: '包码台账',
    description: '打开包码台账，继续查看同批次包件和标签打印记录。',
    icon: 'lucide:list-filter',
    routeName: 'RdPackageUnit',
    query
  })

  if (['CREATED', 'LABELED'].includes(status)) {
    addAction(['rd:production-output:update', 'rd:production-output:confirm'], {
      key: 'production-output',
      label: '生产记录',
      description: '回到生产实绩，核对客户、型号、炉号、炉层和生产班次。',
      icon: 'lucide:factory',
      routeName: 'RdProductionOutput',
      query,
      type: 'primary',
      primary: status === 'CREATED'
    })
  }
  if (status !== 'VOIDED' && trace.value.internalView) {
    addAction(status === 'CREATED' ? ['rd:label-print:print'] : ['rd:label-print:reprint'], {
      key: 'label-print',
      label: status === 'CREATED' ? '打印吨袋标签' : '补打吨袋标签',
      description: '进入包码台账，对该包或同批次标签进行打印、补打。',
      icon: 'lucide:printer',
      routeName: 'RdPackageUnit',
      query: { ...query, labelAction: 'preview' },
      type: 'success',
      primary: status === 'LABELED'
    })
  }

  if (trace.value.qualityInspectionId)
    addAction(['rd:quality-inspection:query'], {
      key: 'quality-history',
      label: '质检记录',
      description: '打开该包关联的质检记录。',
      icon: 'lucide:clipboard-check',
      routeName: pdaMode.value ? 'PdaQuality' : 'RdQualityInspection',
      query: cleanQuery({ ...query, inspectionId: trace.value.qualityInspectionId }),
      type: 'warning'
    })
  if (['LABELED', 'QC_PENDING', 'QC_REJECTED'].includes(status)) {
    addAction(
      [
        'rd:quality-inspection:create',
        'rd:quality-inspection:submit',
        'rd:quality-inspection:approve'
      ],
      {
        key: 'quality-inspection',
        label: '质检登记',
        description: '进入质检模块，按抽检规则登记检测结果、提交或复核放行。',
        icon: 'lucide:clipboard-check',
        routeName: pdaMode.value ? 'PdaQuality' : 'RdQualityInspection',
        query,
        type: 'warning',
        primary: status === 'QC_PENDING'
      }
    )
  }

  if (['QC_PASSED', 'INBOUND_PENDING'].includes(status) && !workContextUnavailable.value) {
    addAction(['rd:putaway-task:create', 'rd:putaway-task:assign', 'rd:putaway-task:confirm'], {
      key: 'putaway-workbench',
      label: status === 'QC_PASSED' ? '安排入库上架' : '处理上架任务',
      description: '进入上架工作台，由仓管员选择/确认库位并处理上架异常。',
      icon: 'lucide:warehouse',
      routeName: 'RdPutawayWorkbench',
      query,
      type: 'success',
      primary: true
    })
    addAction(['rd:inbound-order:create', 'rd:inbound-order:query'], {
      key: 'inbound-order',
      label: '入库单',
      description: '打开入库单模块，核对生产记录、入库数量、重量和库存流水。',
      icon: 'lucide:log-in',
      routeName: 'RdInboundOrder',
      query
    })
  }

  if (status === 'IN_STOCK') {
    if (
      !workContextUnavailable.value &&
      !workContext.value?.tasks.some((task) => task.type === 'LOADING')
    )
      addAction(['rd:loading-task:dispatch'], {
        key: 'loading-dispatch',
        label: '装车派单',
        description: '进入装车派单，按提货单、库位和包件清单安排装车任务。',
        icon: 'lucide:truck',
        routeName: 'RdLoadingDispatch',
        query,
        type: 'primary',
        primary: true
      })
    addAction(['rd:outbound-order:create', 'rd:outbound-order:query'], {
      key: 'outbound-order',
      label: '出库单',
      description: '打开出库单模块，按客户订单或提货需求复核出库。',
      icon: 'lucide:send',
      routeName: 'RdOutboundOrder',
      query
    })
    addAction(['rd:stock-lot:query', 'rd:stock-transaction:query'], {
      key: 'stock-lot',
      label: '库存台账',
      description: '查看该包所在库存批次、库位和库存流水。',
      icon: 'lucide:database',
      routeName: 'RdStockLot',
      query
    })
  }

  if (status === 'OUTBOUNDED') {
    addAction(['rd:outbound-order:query', 'rd:stock-transaction:query'], {
      key: 'outbound-trace',
      label: '出库记录',
      description: '查看出库单、库存流水和库位释放记录。',
      icon: 'lucide:file-check-2',
      routeName: 'RdOutboundOrder',
      query,
      type: 'info'
    })
  }

  return actions
}

const getFieldAction = (status?: RdPackageStatus | string) => {
  const actionMap: Record<
    string,
    {
      title: string
      role: string
      detail: string
      icon: string
    }
  > = {
    CREATED: {
      title: '等待贴码确认',
      role: '生产班长 / 包装岗位',
      detail: '核对客户、型号、炉号、炉层和重量后贴纸质二维码，保持吨袋码面清晰。',
      icon: 'lucide:badge-plus'
    },
    LABELED: {
      title: '等待质检抽检',
      role: '质检部门',
      detail: '二维码已绑定吨袋身份，下一步由质检按批次或抽检规则登记结果。',
      icon: 'lucide:clipboard-check'
    },
    QC_PENDING: {
      title: '待质检放行',
      role: '质检部门',
      detail: '完成检测并提交合格/不合格结论后，系统才允许进入入库流程。',
      icon: 'lucide:flask-conical'
    },
    QC_PASSED: {
      title: '可安排入库',
      role: '仓管员',
      detail: '仓管员选择库位并确认入库；叉车司机按任务搬运，不承担最终库位确认。',
      icon: 'lucide:warehouse'
    },
    INBOUND_PENDING: {
      title: '待入库确认',
      role: '仓管员',
      detail: '扫码核对同批包数、重量和计划库位，确认后更新库存与库位占用。',
      icon: 'lucide:log-in'
    },
    IN_STOCK: {
      title: '在库可查询',
      role: '仓管员 / 商务',
      detail: '可用于库存核对、提货选包和发货前复核，库位变化仍以仓管确认结果为准。',
      icon: 'lucide:map-pin'
    },
    OUTBOUND_PENDING: {
      title: '已进入装车任务',
      role: '仓管员 / 叉车司机',
      detail: '按提货单和装车任务核对客户、包数、库位；现场搬运按任务执行。',
      icon: 'lucide:truck'
    },
    OUTBOUNDED: {
      title: '已出库完成',
      role: '商务 / 客户',
      detail: '可查看发货追溯信息，内部账号可继续核对出库记录与库位释放。',
      icon: 'lucide:send'
    },
    QC_REJECTED: {
      title: '质检异常待处理',
      role: '质检 / 生产',
      detail: '不合格包不得入库或发货，需按质量异常流程复核、隔离或处置。',
      icon: 'lucide:triangle-alert'
    },
    VOIDED: {
      title: '包码已作废',
      role: '系统管理员',
      detail: '该包码不得继续用于入库、出库或客户追溯，需核对作废原因。',
      icon: 'lucide:ban'
    }
  }
  return (
    actionMap[String(status)] || {
      title: '等待现场处理',
      role: '现场岗位',
      detail: '系统已识别吨袋身份，请结合当前任务和岗位权限继续办理。',
      icon: 'lucide:scan-line'
    }
  )
}

const loadBatchContext = async (requestId: number, currentTrace: RdPackageTraceVO | null) => {
  if (requestId !== traceRequestSequence) return
  batchPackages.value = []
  batchUnavailable.value = false
  batchLoading.value = false
  if (!currentTrace?.internalView || !currentTrace.productionOutputId) return
  batchLoading.value = true
  try {
    const packages = await RdPackageUnitApi.trace(currentTrace.productionOutputId)
    if (requestId === traceRequestSequence) batchPackages.value = packages
  } catch {
    if (requestId === traceRequestSequence) batchUnavailable.value = true
  } finally {
    if (requestId === traceRequestSequence) batchLoading.value = false
  }
}

const loadWorkContext = async (requestId: number, code: string) => {
  if (requestId !== traceRequestSequence) return
  workContext.value = null
  workContextUnavailable.value = false
  if (
    !hasAccessToken.value ||
    !hasPermission([
      'rd:package-unit:query',
      'rd:putaway-task:execute',
      'rd:loading-task:execute',
      'rd:pickup-slip:query'
    ])
  )
    return
  try {
    const context = await RdPackageUnitApi.getWorkContext(code)
    if (requestId === traceRequestSequence) workContext.value = context
  } catch {
    if (requestId === traceRequestSequence) workContextUnavailable.value = true
  }
}

const loadTrace = async () => {
  const code = packageCode.value
  if (!code) return
  const requestId = ++traceRequestSequence
  loading.value = true
  let currentTrace: RdPackageTraceVO | null = null
  try {
    currentTrace = canViewInternal.value
      ? await RdPackageUnitApi.getTraceDetail(code)
      : await RdPackageUnitApi.getPublicTrace(code)
  } catch {
    if (canViewInternal.value) {
      try {
        currentTrace = await RdPackageUnitApi.getPublicTrace(code)
      } catch {
        currentTrace = null
      }
    }
  }
  if (requestId !== traceRequestSequence) return
  trace.value = currentTrace
  workContext.value = null
  queryTime.value = formatDate(new Date(), 'YYYY-MM-DD HH:mm:ss')
  await Promise.all([
    loadBatchContext(requestId, currentTrace),
    currentTrace ? loadWorkContext(requestId, code) : Promise.resolve()
  ])
  if (requestId === traceRequestSequence) {
    loading.value = false
  }
}

const goLogin = () => {
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}

const runTraceAction = (action: TraceAction) => {
  if (action.handler === 'login') {
    goLogin()
    return
  }
  if (action.handler === 'refresh') {
    loadTrace()
    return
  }
  if (action.routeName) {
    router.push({ name: action.routeName, query: action.query })
  }
}

const dateTimeText = (value?: string) =>
  value ? formatDate(new Date(value), 'YYYY-MM-DD HH:mm:ss') : '-'

const eventType = (stage: string): TagType =>
  timelineStages.find((item) => item.value === stage)?.type || 'info'

const eventStageLabel = (stage: string) =>
  timelineStages.find((item) => item.value === stage)?.label || stage || '其他'

watch(packageCode, () => {
  timelineStage.value = 'ALL'
})
watch(packageCode, loadTrace, { immediate: true })
</script>

<style scoped lang="scss">
.package-trace-page {
  height: 100%;
  min-height: 100%;
  overflow: auto;
  color: #17212b;
  background: #f3f6f8;
}

.trace-header {
  height: 76px;
  background: #fff;
  border-bottom: 1px solid #dce3e8;
}

.trace-header__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: calc(100% - 40px);
  height: 100%;
  max-width: 1040px;
  margin: 0 auto;
}

.brand {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
}

.brand__logo {
  width: 92px;
  height: 48px;
  object-fit: contain;
}

.brand > div {
  min-width: 0;
}

.brand strong,
.brand span {
  display: block;
}

.brand strong {
  overflow: hidden;
  font-size: 16px;
  line-height: 24px;
  color: #1e3446;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand span {
  font-size: 13px;
  line-height: 20px;
  color: #687985;
}

.header-actions {
  display: flex;
  flex: none;
  gap: 8px;
  align-items: center;
}

.trace-main {
  width: calc(100% - 40px);
  max-width: 1040px;
  padding: 28px 0 48px;
  margin: 0 auto;
}

.trace-surface {
  overflow: hidden;
  background: #fff;
  border: 1px solid #dce3e8;
  border-radius: 8px;
}

.empty-state {
  display: grid;
  min-height: 480px;
  place-items: center;
}

.identity-band {
  display: flex;
  gap: 24px;
  align-items: center;
  justify-content: space-between;
  padding: 28px 32px;
  border-bottom: 1px solid #dce3e8;
  border-left: 5px solid #23924b;
}

.identity-band__main {
  min-width: 0;
}

.identity-eyebrow {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 700;
  color: #2a7651;
}

.identity-band h1 {
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 28px;
  line-height: 38px;
  letter-spacing: 0;
  color: #183447;
}

.identity-band p {
  margin: 6px 0 0;
  font-size: 15px;
  color: #687985;
}

.identity-band__status {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
  font-size: 12px;
  color: #687985;
}

.feature-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-bottom: 1px solid #dce3e8;
}

.trace-feature-card {
  min-width: 0;
  padding: 18px 20px;
  margin: 0;
  border-right: 1px solid #e4e9ed;
}

.trace-feature-card:last-child {
  border-right: 0;
}

.trace-feature-card dt {
  margin-bottom: 6px;
  font-size: 12px;
  line-height: 18px;
  color: #758590;
}

.trace-feature-card dd {
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 18px;
  font-weight: 700;
  line-height: 26px;
  color: #1f394c;
}

.trace-feature-card p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: #7b8993;
}

.detail-section {
  padding: 28px 32px 32px;
  border-bottom: 1px solid #dce3e8;
}

.section-heading {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 22px;
  color: #276b94;
}

.section-heading > :first-child {
  flex: none;
  margin-top: 2px;
  font-size: 21px;
}

.section-heading h2 {
  margin: 0;
  font-size: 18px;
  line-height: 26px;
  letter-spacing: 0;
  color: #1e3446;
}

.section-heading p {
  margin: 2px 0 0;
  font-size: 13px;
  line-height: 20px;
  color: #7b8993;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 0;
  border-top: 1px solid #e4e9ed;
  border-left: 1px solid #e4e9ed;
}

.detail-grid > div {
  min-width: 0;
  padding: 16px;
  border-right: 1px solid #e4e9ed;
  border-bottom: 1px solid #e4e9ed;
}

.detail-grid dt {
  margin-bottom: 6px;
  font-size: 12px;
  line-height: 18px;
  color: #758590;
}

.detail-grid dd {
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
  color: #243847;
}

.detail-grid--internal {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.internal-section {
  background: #f8fbfd;
}

.action-section {
  background: #fbfcfd;
}

.next-action-card {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding: 18px;
  border: 1px solid #d8e5eb;
  border-radius: 8px;
}

.next-action-card__icon {
  display: grid;
  flex: none;
  width: 42px;
  height: 42px;
  font-size: 22px;
  color: #1f6f90;
  background: #eaf6f1;
  border-radius: 8px;
  place-items: center;
}

.next-action-card__body {
  min-width: 0;
}

.next-action-title {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
  margin-bottom: 6px;
}

.next-action-title strong {
  font-size: 17px;
  line-height: 25px;
  color: #1e3446;
}

.next-action-title span {
  padding: 2px 8px;
  font-size: 12px;
  line-height: 18px;
  color: #1f6f90;
  background: #eaf6f1;
  border: 1px solid #cbe7da;
  border-radius: 999px;
}

.next-action-card__body p {
  margin: 0;
  font-size: 14px;
  line-height: 22px;
  color: #546775;
}

.action-rules {
  padding-left: 18px;
  margin: 12px 0 0;
  font-size: 13px;
  line-height: 22px;
  color: #687985;
}

.action-entry-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.action-entry-list :deep(.el-button) {
  margin-left: 0;
}

.action-entry-empty {
  padding: 10px 12px;
  margin: 14px 0 0;
  font-size: 13px;
  line-height: 20px;
  color: #687985;
  background: #f7f9fa;
  border: 1px dashed #d2dde4;
  border-radius: 8px;
}

.batch-section {
  background: #fff;
}

.batch-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.batch-summary-card {
  min-width: 0;
  padding: 14px 16px;
  background: #f8fbfd;
  border: 1px solid #e1e8ed;
  border-radius: 8px;
}

.batch-summary-card span,
.batch-more {
  font-size: 12px;
  line-height: 18px;
  color: #758590;
}

.batch-summary-card strong {
  display: block;
  margin-top: 6px;
  overflow: hidden;
  font-size: 18px;
  line-height: 26px;
  color: #1e3446;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-package-list {
  overflow: hidden;
  border: 1px solid #e1e8ed;
  border-radius: 8px;
}

.batch-package-row {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #e8edf1;
}

.batch-package-row:last-child {
  border-bottom: 0;
}

.batch-package-row--current {
  background: #f0f7f3;
}

.batch-package-main,
.batch-package-meta {
  display: flex;
  min-width: 0;
  align-items: center;
}

.batch-package-main {
  flex: 1;
  gap: 10px;
}

.batch-package-main strong {
  overflow-wrap: anywhere;
  font-size: 14px;
  line-height: 22px;
  color: #1e3446;
}

.batch-package-main span,
.batch-package-meta span {
  font-size: 12px;
  line-height: 18px;
  color: #687985;
}

.batch-package-meta {
  flex: none;
  gap: 10px;
}

.batch-more {
  margin: 12px 0 0;
}

.quality-result {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  max-width: 100%;
}

.quality-result__text {
  min-width: 0;
}

.quality-result--success {
  color: #237c45;
}

.quality-result--danger {
  color: #c03d3d;
}

.quality-result--pending {
  color: #a56817;
}

.timeline-section {
  border-bottom: 0;
}

.timeline-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 20px;
  font-size: 13px;
  color: #687985;
}

.timeline-filter__select {
  width: 160px;
  max-width: 100%;
}

.trace-timeline {
  max-width: 760px;
  padding-left: 2px;
}

.timeline-milestone {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 8px;
  align-items: flex-start;
}

.timeline-milestone :deep(.el-tag) {
  flex: none;
  height: auto;
  max-width: 100%;
  min-height: 20px;
  white-space: normal;
}

.trace-timeline strong {
  display: block;
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 15px;
  line-height: 22px;
  color: #243847;
}

.trace-timeline p {
  margin: 4px 0 0;
  overflow-wrap: anywhere;
  font-size: 13px;
  line-height: 20px;
  color: #687985;
}

.trace-timeline :deep(.el-timeline-item__timestamp),
.timeline-milestone :deep(.el-tag__content) {
  overflow-wrap: anywhere;
}

.trace-footer {
  display: flex;
  justify-content: space-between;
  padding: 14px 32px;
  font-size: 12px;
  line-height: 18px;
  color: #7b8993;
  background: #f7f9fa;
  border-top: 1px solid #dce3e8;
}

@media (width <= 760px) {
  .trace-header {
    height: 68px;
  }

  .trace-header__inner,
  .trace-main {
    width: calc(100% - 24px);
  }

  .brand__logo {
    width: 70px;
    height: 40px;
  }

  .brand strong {
    max-width: 160px;
    font-size: 14px;
  }

  .brand span {
    display: none;
  }

  .header-actions :deep(.el-button--primary) {
    padding: 8px 10px;
  }

  .trace-main {
    padding: 16px 0 32px;
  }

  .identity-band {
    align-items: flex-start;
    padding: 22px 18px;
  }

  .identity-band h1 {
    font-size: 22px;
    line-height: 30px;
  }

  .identity-band__status {
    align-items: flex-end;
  }

  .identity-band__status span {
    display: none;
  }

  .feature-strip,
  .batch-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .trace-feature-card:nth-child(2n) {
    border-right: 0;
  }

  .trace-feature-card:nth-child(n + 3) {
    border-top: 1px solid #e4e9ed;
  }

  .detail-section {
    padding: 24px 18px;
  }

  .detail-grid,
  .detail-grid--internal {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-grid > div {
    padding: 13px 12px;
  }

  .next-action-card,
  .batch-package-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .action-entry-list {
    width: 100%;
  }

  .action-entry-list :deep(.el-button) {
    justify-content: flex-start;
    min-height: 40px;
  }

  .batch-package-meta {
    flex-wrap: wrap;
  }

  .trace-footer {
    flex-direction: column;
    gap: 4px;
    padding: 12px 18px;
  }
}

@media (width <= 420px) {
  .brand strong {
    max-width: 118px;
  }

  .header-actions :deep(.el-button--primary span) {
    font-size: 12px;
  }

  .identity-band {
    flex-direction: column;
    gap: 14px;
  }

  .identity-band__status {
    align-items: flex-start;
  }

  .feature-strip,
  .batch-summary-grid {
    grid-template-columns: 1fr;
  }

  .trace-feature-card,
  .trace-feature-card:nth-child(2n) {
    border-right: 0;
  }

  .trace-feature-card + .trace-feature-card {
    border-top: 1px solid #e4e9ed;
  }

  .action-entry-list :deep(.el-button) {
    width: 100%;
  }
}
</style>
