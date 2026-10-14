import { ref, onBeforeUnmount } from 'vue'
import { getAccessToken, getAuthContextRevision, onAuthContextChange } from '@/utils/auth'

export const useQrAuthBoundary = (clear: () => void) => {
  const captured = getAuthContextRevision()
  const valid = ref(true)
  const current = () => valid.value && !!getAccessToken() && getAuthContextRevision() === captured
  const stop = onAuthContextChange(() => {
    valid.value = false
    clear()
  })
  onBeforeUnmount(() => {
    valid.value = false
    stop()
  })
  return { valid, current }
}
