import { createApp, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import LocalDocumentReader from '@ocr-component'
import { changeAuth } from './auth'
const app = createApp(LocalDocumentReader)
app.use(ElementPlus)
app.component('Icon', { render: () => h('span', { 'aria-hidden': 'true', style: 'display:inline-block;width:16px;height:16px;flex-shrink:0' }) })
app.mount('#app')
Object.assign(window, { ocrHarness: { changeAuth } })
