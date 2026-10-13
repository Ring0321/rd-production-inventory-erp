<template>
  <section class="passport-section" aria-labelledby="passport-heading" :aria-busy="busy">
    <header class="passport-heading">
      <div><Icon icon="lucide:link-2" /><h2 id="passport-heading">二维码资源与公开护照</h2></div>
      <el-tooltip content="刷新公开护照" placement="top"
        ><el-button circle :disabled="busy" aria-label="刷新公开护照" @click="load"
          ><Icon icon="lucide:refresh-cw" /></el-button
      ></el-tooltip>
    </header>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <div v-if="current" class="passport-data">
      <dl
        ><dt>原标签身份链接</dt><dd>{{ current.document['@id'] }}</dd
        ><dt>公开内容摘要</dt
        ><dd
          ><code>{{ current.passport.digest }}</code></dd
        ><dt>快照读取时间</dt><dd>{{ current.passport.generatedAt }}</dd></dl
      >
      <div class="passport-tools">
        <el-button :disabled="busy" @click="download"
          ><Icon icon="lucide:download" class="mr-6px" />下载公开护照</el-button
        >
        <el-button :disabled="busy" @click="copyIdentity"
          ><Icon icon="lucide:copy" class="mr-6px" />复制标签链接</el-button
        >
        <el-button :disabled="busy" @click="openResource('links')"
          ><Icon icon="lucide:list-tree" class="mr-6px" />资源目录</el-button
        >
        <el-button :disabled="busy" @click="openResource('document')"
          ><Icon icon="lucide:file-json" class="mr-6px" />机器文档</el-button
        >
      </div>
      <p class="passport-meta"
        >公开快照不含客户、库位、单据或人员信息；摘要不是来源签名或实物防伪。</p
      >
    </div>
    <div class="passport-check">
      <h3>本机文件核对</h3>
      <div class="passport-tools">
        <input
          ref="fileInput"
          type="file"
          accept="application/json,.json"
          aria-label="选择本机护照文件"
          @change="readFile"
        />
        <el-button :disabled="busy" @click="fileInput?.click()"
          ><Icon icon="lucide:folder-open" class="mr-6px" />选择本机文件</el-button
        >
        <el-button :disabled="busy || !localFile" @click="compare"
          ><Icon icon="lucide:server-cog" class="mr-6px" />核对服务器当前记录</el-button
        >
      </div>
      <p v-if="checkMessage" class="passport-result" role="status">{{ checkMessage }}</p>
      <p class="passport-meta"
        >文件留在本机；核对只请求当前服务器的包件公开数据，不上传文件、不办理出入库。</p
      >
    </div>
  </section>
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import {
  getPublicPassport,
  verifyPassport,
  PASSPORT_MAX_BYTES,
  type PackagePassport
} from '@/api/rd/passport'
import { onAuthContextChange, getAuthContextRevision } from '@/utils/auth'
const props = defineProps<{ packageCode: string; factsRevision: string | number }>()
const current = ref<Awaited<ReturnType<typeof getPublicPassport>>>()
const localFile = ref<PackagePassport>()
const fileInput = ref<HTMLInputElement>()
const busy = ref(false)
const error = ref('')
const checkMessage = ref('')
let sequence = 0
let alive = true
let abort: AbortController | undefined
const stamp = () => ({ sequence, code: props.packageCode, revision: getAuthContextRevision() })
const valid = (s: ReturnType<typeof stamp>) =>
  alive &&
  s.sequence === sequence &&
  s.code === props.packageCode &&
  s.revision === getAuthContextRevision()
const reset = () => {
  sequence++
  abort?.abort()
  busy.value = false
  current.value = undefined
  localFile.value = undefined
  error.value = ''
  checkMessage.value = ''
  if (fileInput.value) fileInput.value.value = ''
}
const load = async () => {
  reset()
  const s = stamp()
  abort = new AbortController()
  busy.value = true
  try {
    const data = await getPublicPassport(s.code, abort.signal)
    if (valid(s)) current.value = data
  } catch {
    if (valid(s)) error.value = '公开护照暂不可用，原追溯与业务办理不受影响'
  } finally {
    if (valid(s)) busy.value = false
  }
}
const download = () => {
  if (!current.value || busy.value) return
  const url = URL.createObjectURL(
    new Blob([JSON.stringify(current.value.passport, null, 2)], { type: 'application/json' })
  )
  const a = document.createElement('a')
  a.href = url
  a.download = `duoxu-passport-${props.packageCode}.json`
  a.click()
  URL.revokeObjectURL(url)
}
const copyIdentity = async () => {
  if (!current.value || busy.value) return
  const s = stamp()
  try {
    await navigator.clipboard.writeText(current.value.document['@id'])
    if (valid(s)) checkMessage.value = '标签身份链接已复制'
  } catch {
    if (valid(s)) checkMessage.value = '当前环境不支持复制，请查看上方标签链接'
  }
}
const openResource = (resource: 'links' | 'document') =>
  window.open(
    `${import.meta.env.BASE_URL}pkg/${encodeURIComponent(props.packageCode)}?resource=${resource}`,
    '_blank',
    'noopener,noreferrer'
  )
const readFile = async (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || busy.value) return
  const s = stamp()
  localFile.value = undefined
  checkMessage.value = ''
  busy.value = true
  try {
    if (file.size > PASSPORT_MAX_BYTES * 2) throw new Error('文件过大')
    const result = await verifyPassport(JSON.parse(await file.text()), s.code)
    if (valid(s)) {
      localFile.value = result.passport
      checkMessage.value = '文件内容摘要一致，尚未核对服务器当前记录；这不是来源签名或防伪结论'
    }
  } catch (e) {
    if (valid(s)) checkMessage.value = e instanceof Error ? e.message : '文件无法核对'
  } finally {
    if (valid(s)) busy.value = false
  }
}
const compare = async () => {
  if (!localFile.value || busy.value) return
  const original = localFile.value
  const s = stamp()
  busy.value = true
  abort = new AbortController()
  try {
    const latest = await getPublicPassport(s.code, abort.signal)
    await verifyPassport(latest.passport, s.code)
    if (valid(s)) {
      current.value = latest
      checkMessage.value =
        latest.passport.digest === original.digest
          ? '文件公开内容与服务器当前记录一致；仍不证明实物真伪或签名'
          : '文件与服务器当前公开记录不同，可能记录已更新或文件内容变化，请核对来源'
    }
  } catch {
    if (valid(s)) checkMessage.value = '服务器未核实，仅保留本机文件摘要结果，不作一致性结论'
  } finally {
    if (valid(s)) busy.value = false
  }
}
const stopAuth = onAuthContextChange(reset)
watch(() => [props.packageCode, props.factsRevision], load, { immediate: true })
onBeforeUnmount(() => {
  alive = false
  reset()
  stopAuth()
})
</script>
<style scoped lang="scss">
.passport-section {
  padding: 24px 32px;
  letter-spacing: 0;
  border-bottom: 1px solid var(--el-border-color-light);
}

.passport-heading,
.passport-heading > div,
.passport-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.passport-heading {
  justify-content: space-between;
  margin-bottom: 16px;
}

.passport-heading h2 {
  margin: 0;
  font-size: 18px;
  line-height: 26px;
}

.passport-data dl {
  margin: 0;
}

.passport-data dt,
.passport-meta {
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
}

.passport-data dd {
  margin: 4px 0 12px;
  overflow-wrap: anywhere;
  font-size: 13px;
  line-height: 22px;
}

.passport-data code {
  font-size: 12px;
}

.passport-tools :deep(.el-button) {
  margin-left: 0;
}

.passport-check {
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.passport-check h3 {
  margin: 0 0 12px;
  font-size: 14px;
}

.passport-tools input {
  display: none;
}

.passport-result {
  font-size: 13px;
  line-height: 22px;
  color: var(--el-text-color-primary);
  overflow-wrap: anywhere;
}

.passport-meta {
  margin: 8px 0 0;
}

@media (width <= 760px) {
  .passport-section {
    padding: 24px 18px;
  }
}
</style>
