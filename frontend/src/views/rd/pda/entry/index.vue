<template><div v-loading="true" class="pda-entry-loading"></div></template>
<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
defineOptions({ name: 'RdPdaEntry' })
const router = useRouter()
onMounted(() => router.replace({ name: 'PdaWorkspace' }))
</script>
<style scoped>
.pda-entry-loading {
  min-height: 160px;
}
</style>
