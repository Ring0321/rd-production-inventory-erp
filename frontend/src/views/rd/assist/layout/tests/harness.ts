import { createApp, defineComponent, h, ref } from 'vue'
import 'element-plus/dist/index.css'
import WarehouseLayoutEditor from '../WarehouseLayoutEditor.vue'
import LabelTemplateStudio from '../../templates/LabelTemplateStudio.vue'
import { fixture } from './fixture'
Reflect.set(window, '__reuseEditorFixture', fixture)

const app = defineComponent({
  setup() {
    const tab = ref('layout')
    const key = ref(0)
    const choose = (value: string) => {
      tab.value = value
      fixture.resetAuth()
      key.value++
    }
    return () =>
      h(
        'main',
        {
          style:
            'padding:12px;max-width:1400px;margin:auto;font-family:Arial,sans-serif;color:#1c2630;letter-spacing:0'
        },
        [
          h('nav', { style: 'display:flex;flex-wrap:wrap;gap:8px;margin-bottom:16px' }, [
            h('button', { onClick: () => choose('layout') }, 'Layout fixture'),
            h('button', { onClick: () => choose('label') }, 'Label fixture'),
            h('button', { onClick: () => fixture.revoke() }, 'Revoke fixture'),
            h(
              'button',
              {
                onClick: () => {
                  fixture.conflict = !fixture.conflict
                }
              },
              'Conflict fixture'
            ),
            h(
              'button',
              {
                onClick: () => {
                  fixture.unknown = !fixture.unknown
                }
              },
              'Unknown audit fixture'
            )
          ]),
          h(tab.value === 'layout' ? WarehouseLayoutEditor : LabelTemplateStudio, {
            key: key.value
          })
        ]
      )
  }
})
createApp(app).mount('#app')
