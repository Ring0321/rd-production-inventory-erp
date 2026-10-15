<script setup lang="ts">
import { fieldKeys } from '@/api/rd/documentocr'
import { useLocalDocumentReader } from './useLocalDocumentReader'

defineOptions({ name: 'LocalDocumentReader' })
const {
  input,
  file,
  preview,
  kind,
  result,
  status,
  busy,
  error,
  draft,
  allowed,
  clear,
  selectFile,
  recognize,
  refreshStatus,
  exportDraft
} = useLocalDocumentReader()
const labels = {
  customer: '客户',
  order: '订单 / 提货单号',
  model: '型号',
  bagCount: '袋数',
  weightKg: '净重（kg）'
}
const statusLabels: Record<string, string> = {
  DISABLED: '未启用',
  UNAVAILABLE: '不可用',
  MODELS_OR_RUNTIME_MISSING: '模型或运行时缺失',
  LOCAL_FILES_READY: '本地模型就绪',
  INFERENCE_FAILED: '推理不可用'
}
</script>

<template>
  <section class="document-reader" aria-label="本地单据识别">
    <div class="reader-toolbar">
      <h3>本地单据识别</h3>
      <el-tag :type="status?.available ? 'success' : 'info'">
        {{ status ? statusLabels[status.state] || '不可用' : '未检查' }}
      </el-tag>
      <el-button
        :disabled="!allowed || busy"
        aria-label="刷新服务状态"
        title="刷新服务状态"
        @click="refreshStatus"
      >
        <Icon icon="ep:refresh" />
      </el-button>
      <span class="toolbar-spacer"></span>
      <el-button :disabled="!allowed" aria-label="清空草稿" title="清空草稿" @click="clear"
        ><Icon icon="ep:delete"
      /></el-button>
      <el-button :disabled="!allowed || !result || busy" @click="exportDraft"
        ><Icon icon="ep:download" />导出草稿 JSON</el-button
      >
    </div>
    <el-alert v-if="!allowed" title="无单据创建权限" type="warning" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <div class="reader-filebar">
      <input
        ref="input"
        class="native-file"
        type="file"
        accept="image/png,image/jpeg,application/pdf"
        aria-label="选择本地单据"
        :disabled="!allowed || busy"
        @change="selectFile"
      />
      <el-button :disabled="!allowed || busy" @click="input?.click()"
        ><Icon icon="ep:upload" />选择单据</el-button
      >
      <span class="selected-file">{{ file?.name || '未选择文件' }}</span>
      <el-button
        type="primary"
        :loading="busy"
        :disabled="!allowed || !file || !status?.available"
        @click="recognize"
      >
        <Icon icon="ep:document-checked" />识别
      </el-button>
    </div>
    <div class="reader-columns">
      <div class="reader-source">
        <h4>原件</h4>
        <div class="source-preview">
          <img v-if="preview" :src="preview" alt="本地单据预览" referrerpolicy="no-referrer" />
          <div v-else class="source-placeholder">
            <Icon :icon="kind === 'application/pdf' ? 'ep:document' : 'ep:picture'" :size="36" />
            <span>{{ file ? 'PDF' : '未选择文件' }}</span>
          </div>
        </div>
        <p v-if="file" class="file-name"
          >{{ file.name }} · {{ (file.size / 1048576).toFixed(2) }} MiB</p
        >
        <h4
          >识别原文 <span v-if="result">{{ result.pages }} 页</span></h4
        >
        <ol v-if="result?.rawLines.length" class="raw-lines">
          <li v-for="line in result.rawLines" :key="line.id">
            <span class="line-id">{{ line.id }}</span
            ><span>{{ line.text }}</span>
            <small v-if="line.confidence !== undefined"
              >{{ (line.confidence * 100).toFixed(1) }}%</small
            >
          </li>
        </ol>
        <div v-else class="empty-state" role="status">{{ busy ? '正在识别' : '暂无识别结果' }}</div>
      </div>
      <div class="reader-fields">
        <h4>候选与人工草稿</h4>
        <el-form label-position="top" :disabled="!allowed || busy || !result">
          <el-form-item v-for="key in fieldKeys" :key="key" :label="labels[key]">
            <el-input
              v-model="draft[key]"
              :maxlength="160"
              :aria-label="labels[key]"
              placeholder="未知"
            />
            <div v-if="result" class="candidates">
              <span v-if="!result.candidates[key].candidates.length" class="unknown">未知</span>
              <div
                v-for="(candidate, index) in result.candidates[key].candidates"
                :key="index"
                class="candidate"
              >
                <div class="candidate-value"
                  ><span>{{ candidate.value }}</span>
                  <el-button
                    text
                    :disabled="!allowed || busy"
                    :aria-label="`采用${labels[key]}候选 ${index + 1}`"
                    title="采用候选"
                    @click="draft[key] = candidate.value"
                    ><Icon icon="ep:check"
                  /></el-button>
                </div>
                <small
                  >{{ candidate.sourceLineIds.join(', ')
                  }}<template v-if="candidate.confidence !== undefined">
                    · {{ (candidate.confidence * 100).toFixed(1) }}%</template
                  ></small
                >
                <blockquote>{{ candidate.evidence }}</blockquote>
              </div>
            </div>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </section>
</template>

<style scoped>
.document-reader {
  min-width: 0;
  color: var(--el-text-color-primary);
}

.reader-toolbar,
.reader-filebar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.reader-toolbar h3 {
  margin: 0 8px 0 0;
  font-size: 18px;
  line-height: 28px;
}

.toolbar-spacer {
  flex: 1;
}

.native-file {
  display: none;
}

.selected-file {
  min-width: 0;
  flex: 1;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.reader-columns {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: 24px;
}

.reader-source,
.reader-fields {
  min-width: 0;
}

h4 {
  margin: 0 0 12px;
  font-size: 14px;
}

h4 span {
  margin-left: 8px;
  font-weight: normal;
}

.source-preview {
  display: flex;
  height: 280px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  align-items: center;
  justify-content: center;
}

.source-preview img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.source-placeholder {
  display: flex;
  gap: 8px;
  flex-direction: column;
  align-items: center;
  color: var(--el-text-color-secondary);
}

.file-name {
  margin: 8px 0 20px;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.raw-lines {
  max-height: 540px;
  padding: 0;
  margin: 0;
  overflow: auto;
  list-style: none;
}

.raw-lines li {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) 45px;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  overflow-wrap: anywhere;
}

.line-id,
small,
.unknown,
.empty-state {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.empty-state {
  padding: 24px 0;
}

.candidates {
  width: 100%;
  margin-top: 6px;
  line-height: 1.4;
}

.candidate {
  padding: 6px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.candidate-value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  overflow-wrap: anywhere;
}

.candidate-value span {
  min-width: 0;
}

blockquote {
  padding-left: 8px;
  margin: 4px 0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  border-left: 2px solid var(--el-border-color);
  overflow-wrap: anywhere;
}

@media (width <= 760px) {
  .reader-columns {
    grid-template-columns: minmax(0, 1fr);
  }

  .toolbar-spacer {
    display: none;
  }

  .reader-toolbar :deep(.el-button) {
    margin-left: 0;
  }
}
</style>
